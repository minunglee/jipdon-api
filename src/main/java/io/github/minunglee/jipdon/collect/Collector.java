package io.github.minunglee.jipdon.collect;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.minunglee.jipdon.JipdonProperties;
import io.github.minunglee.jipdon.analysis.CacheVersion;
import io.github.minunglee.jipdon.collect.CollectLogStore.Task;
import io.github.minunglee.jipdon.deal.BuildingKey;
import io.github.minunglee.jipdon.deal.BuildingKind;
import io.github.minunglee.jipdon.deal.DealStore;
import io.github.minunglee.jipdon.region.Region;
import io.github.minunglee.jipdon.region.Regions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

/**
 * 실거래가 수집 배치.
 *
 * 한 번 실행할 때마다 할 일을 정해 차례로 처리한다.
 * 1. 최근 3개월: 늦게 신고된 거래와 계약 해제를 반영하려고, 마지막 수집 후 20시간이 지났으면 다시 받는다
 * 2. 그 이전(24개월까지): 아직 안 받은 달만 받는다. 최근 달부터 거꾸로 채워, 처음 배포 직후에도 최근 데이터가 먼저 쌓인다
 *
 * 한 번에 부르는 횟수를 제한하고, 하루 한도 초과 응답이 오면 멈췄다가 다음 실행에서 이어서 한다.
 */
@Service
public class Collector {

	private static final Logger log = LoggerFactory.getLogger(Collector.class);

	private final RtmsClient client;

	private final DealStore dealStore;

	private final CollectLogStore logStore;

	private final CacheVersion cacheVersion;

	private final JipdonProperties.Collect properties;

	private final Clock clock;

	private final AtomicBoolean running = new AtomicBoolean();

	Collector(RtmsClient client, DealStore dealStore, CollectLogStore logStore, CacheVersion cacheVersion,
			JipdonProperties properties, Clock clock) {
		this.client = client;
		this.dealStore = dealStore;
		this.logStore = logStore;
		this.cacheVersion = cacheVersion;
		this.properties = properties.collect();
		this.clock = clock;
	}

	public record Result(int tasks, int calls, int changedRows, int failedTasks, boolean stoppedByQuota,
			int remainingTasks) {
	}

	/** 이번 실행에서 할 일 (순서대로) */
	List<Task> plan() {
		YearMonth current = YearMonth.now(clock);
		Instant now = clock.instant();
		Map<Task, Instant> logs = logStore.all();
		List<Task> refresh = new ArrayList<>();
		List<Task> backlog = new ArrayList<>();
		for (int back = 0; back < properties.historyMonths(); back++) {
			YearMonth month = current.minusMonths(back);
			boolean recent = back < properties.refreshMonths();
			for (Region region : Regions.ALL) {
				for (DealKind kind : DealKind.values()) {
					Task task = new Task(region.code(), kind, month);
					Instant last = logs.get(task);
					if (recent && (last == null || last.isBefore(now.minus(properties.refreshInterval())))) {
						refresh.add(task);
					}
					else if (!recent && last == null) {
						backlog.add(task);
					}
				}
			}
		}
		refresh.addAll(backlog);
		return refresh;
	}

	/** 한 번 수집한다. 이미 실행 중이면 건너뛴다 */
	public Result run() {
		if (!client.configured()) {
			log.warn("공공데이터포털 인증키가 없어 수집을 건너뜁니다");
			return new Result(0, 0, 0, 0, false, -1);
		}
		if (!running.compareAndSet(false, true)) {
			log.info("이전 수집이 아직 진행 중이라 이번 실행은 건너뜁니다");
			return new Result(0, 0, 0, 0, false, -1);
		}
		try {
			return collect(plan());
		}
		finally {
			running.set(false);
		}
	}

	private Result collect(List<Task> tasks) {
		long started = System.nanoTime();
		int calls = 0;
		int changed = 0;
		int failed = 0;
		int done = 0;
		boolean quota = false;
		// 지역·종류마다 건물 id를 한 번만 읽는다
		Map<BuildingKind, Map<String, Map<BuildingKey, Long>>> buildingCache = new EnumMap<>(BuildingKind.class);

		for (Task task : tasks) {
			if (calls >= properties.maxCallsPerRun()) {
				break;
			}
			try {
				Map<BuildingKey, Long> buildings = buildingCache
					.computeIfAbsent(task.kind().buildingKind(), k -> new HashMap<>())
					.computeIfAbsent(task.sggCd(), sgg -> dealStore.loadBuildings(task.kind().buildingKind(), sgg));
				int rows = 0;
				int page = 1;
				while (true) {
					RtmsPage result = client.fetch(task.kind(), task.sggCd(), task.month(), page);
					calls++;
					changed += dealStore.save(task.kind(), task.sggCd(), result.items(), buildings);
					rows += result.items().size();
					if (result.items().isEmpty() || (long) page * client.pageSize() >= result.totalCount()) {
						break;
					}
					page++;
				}
				logStore.record(task, rows, clock.instant());
				done++;
			}
			catch (RtmsApiException ex) {
				forgetBuildings(buildingCache, task);
				if (ex.quotaExceeded()) {
					log.warn("공공데이터포털 하루 호출 한도에 닿아 수집을 멈춥니다. 다음 실행에서 이어서 합니다");
					quota = true;
					break;
				}
				failed++;
				log.warn("수집 실패 {}: {}", task, ex.getMessage());
			}
			catch (RuntimeException ex) {
				forgetBuildings(buildingCache, task);
				failed++;
				log.warn("수집 실패 {}: {}", task, ex.toString());
			}
		}
		if (changed > 0) {
			cacheVersion.bump(); // 계산 결과 캐시를 한 번에 무효화
		}
		Result result = new Result(done, calls, changed, failed, quota, tasks.size() - done - failed);
		log.info("실거래가 수집 완료 {} ({}초)", result, (System.nanoTime() - started) / 1_000_000_000);
		return result;
	}

	/** 저장이 롤백됐으면 그 사이 새로 받은 건물 id도 없던 일이 되므로, 다음에 DB에서 다시 읽게 한다 */
	private static void forgetBuildings(Map<BuildingKind, Map<String, Map<BuildingKey, Long>>> cache, Task task) {
		Map<String, Map<BuildingKey, Long>> byRegion = cache.get(task.kind().buildingKind());
		if (byRegion != null) {
			byRegion.remove(task.sggCd());
		}
	}

}
