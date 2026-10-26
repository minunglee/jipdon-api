package io.github.minunglee.jipdon.collect;

import java.time.Instant;
import java.util.List;

import io.github.minunglee.jipdon.InternalToken;
import io.github.minunglee.jipdon.JipdonProperties;
import io.github.minunglee.jipdon.region.Regions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 게이트웨이 관리자 화면이 읽는 수집 현황 (내부 토큰 필요) */
@RestController
class CollectStatusController {

	private final Collector collector;

	private final CollectLogStore logStore;

	private final CollectRunStore runStore;

	private final JipdonProperties.Collect properties;

	private final InternalToken internalToken;

	CollectStatusController(Collector collector, CollectLogStore logStore, CollectRunStore runStore,
			JipdonProperties properties, InternalToken internalToken) {
		this.collector = collector;
		this.logStore = logStore;
		this.runStore = runStore;
		this.properties = properties.collect();
		this.internalToken = internalToken;
	}

	/**
	 * @param totalTasks 지역 × 종류 × 보관 개월 수
	 * @param refreshDue 최근 달 중 다시 받을 때가 된 일
	 * @param missing 아직 한 번도 받지 않은 달
	 */
	record CollectStatus(boolean enabled, boolean keyConfigured, String cron, int maxCallsPerRun,
			Instant lastCollectedAt, int totalTasks, int refreshDue, int missing, List<CollectRunStore.Run> recentRuns) {
	}

	/** GET /internal/collect: 관리자 화면(게이트웨이)이 읽는 수집 현황. 토큰이 틀리면 404 */
	@GetMapping("/internal/collect")
	ResponseEntity<CollectStatus> status(@RequestHeader(value = "X-Internal-Token", required = false) String token) {
		if (!internalToken.matches(token)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		Collector.Plan plan = collector.planParts();
		int total = Regions.ALL.size() * DealKind.values().length * properties.historyMonths();
		return ResponseEntity.ok(new CollectStatus(properties.enabled(), collector.keyConfigured(), properties.cron(),
				properties.maxCallsPerRun(), logStore.lastCollectedAt(), total, plan.refresh().size(),
				plan.backlog().size(), runStore.recent(10)));
	}

}
