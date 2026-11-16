package io.github.minunglee.jipdon.collect;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

import io.github.minunglee.jipdon.analysis.CacheVersion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 쌓인 건물·실거래 건수 (jipdon.com "데이터 현황" 한 줄과 관리자 화면).
 * <p>
 * 수백만 건을 세는 데 운영 서버에서 2초 가까이 걸려서, 센 결과를 들고 있다가 거래가 바뀌었을 때만 다시 센다.
 * 수집이 새 거래를 넣으면 캐시 버전을 올리므로, 버전이 같으면 건수도 같다.
 * 공개 화면이 페이지마다 부르므로 다시 세는 일은 한 번에 하나만 하고, 나머지 요청은 그 결과를 같이 쓴다.
 */
@Repository
public class DealCountStore {

	/** Redis를 못 읽어 버전을 모를 때, 지난번 결과를 이만큼은 그대로 쓴다 (페이지마다 2초씩 세지 않게) */
	static final Duration FALLBACK_TTL = Duration.ofMinutes(10);

	private final JdbcTemplate jdbc;

	private final CacheVersion cacheVersion;

	private final Clock clock;

	/** 다시 세는 일을 한 번에 하나만 (가상 스레드에서 synchronized 대신 Lock) */
	private final ReentrantLock recount = new ReentrantLock();

	/** 마지막으로 센 결과와 그때의 캐시 버전 */
	private volatile Snapshot snapshot;

	DealCountStore(JdbcTemplate jdbc, CacheVersion cacheVersion, Clock clock) {
		this.jdbc = jdbc;
		this.cacheVersion = cacheVersion;
		this.clock = clock;
	}

	/**
	 * 종류별 건수
	 *
	 * @param buildings 건물(단지) 수
	 * @param sale 매매 (아파트·연립다세대·오피스텔)
	 * @param rent 전월세 (아파트·연립다세대·오피스텔)
	 * @param detached 단독·다가구 매매·전월세
	 * @param countedAt 센 시각
	 */
	public record DealCounts(long buildings, long sale, long rent, long detached, Instant countedAt) {

		public long total() {
			return sale + rent + detached;
		}

	}

	private record Snapshot(String version, DealCounts counts) {
	}

	/** 거래가 바뀌지 않았으면 지난번에 센 값을 그대로 돌려준다 */
	public DealCounts counts() {
		String version = cacheVersion.current();
		Snapshot current = snapshot;
		if (isFresh(current, version)) {
			return current.counts();
		}
		recount.lock();
		try {
			current = snapshot; // 기다리는 동안 다른 요청이 이미 셌으면 그 결과를 쓴다
			if (isFresh(current, version)) {
				return current.counts();
			}
			DealCounts counts = jdbc.queryForObject("""
					SELECT (SELECT count(*) FROM building), (SELECT count(*) FROM sale_deal),
					       (SELECT count(*) FROM rent_deal), (SELECT count(*) FROM detached_deal)
					""", (rs, i) -> new DealCounts(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
					clock.instant()));
			snapshot = new Snapshot(version, counts);
			return counts;
		}
		finally {
			recount.unlock();
		}
	}

	/** 버전이 같으면 최신. Redis를 못 읽으면(버전 null) 센 지 10분 안이면 최신으로 본다 */
	private boolean isFresh(Snapshot current, String version) {
		if (current == null) {
			return false;
		}
		if (version == null) {
			return current.counts().countedAt().isAfter(clock.instant().minus(FALLBACK_TTL));
		}
		return version.equals(current.version());
	}

}
