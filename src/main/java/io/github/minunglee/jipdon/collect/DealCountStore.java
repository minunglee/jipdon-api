package io.github.minunglee.jipdon.collect;

import java.time.Clock;
import java.time.Instant;

import io.github.minunglee.jipdon.analysis.CacheVersion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 쌓인 실거래 건수 (관리자 화면용).
 * <p>
 * 수백만 건을 세는 데 운영 서버에서 2초 가까이 걸려서, 센 결과를 들고 있다가 거래가 바뀌었을 때만 다시 센다.
 * 수집이 새 거래를 넣으면 캐시 버전을 올리므로, 버전이 같으면 건수도 같다.
 */
@Repository
public class DealCountStore {

	private final JdbcTemplate jdbc;

	private final CacheVersion cacheVersion;

	private final Clock clock;

	/** 마지막으로 센 결과와 그때의 캐시 버전 */
	private volatile Snapshot snapshot;

	DealCountStore(JdbcTemplate jdbc, CacheVersion cacheVersion, Clock clock) {
		this.jdbc = jdbc;
		this.cacheVersion = cacheVersion;
		this.clock = clock;
	}

	/**
	 * 종류별 거래 건수
	 *
	 * @param sale 매매 (아파트·연립다세대·오피스텔)
	 * @param rent 전월세 (아파트·연립다세대·오피스텔)
	 * @param detached 단독·다가구 매매·전월세
	 * @param countedAt 센 시각
	 */
	public record DealCounts(long sale, long rent, long detached, Instant countedAt) {

		public long total() {
			return sale + rent + detached;
		}

	}

	private record Snapshot(String version, DealCounts counts) {
	}

	/** 거래가 바뀌지 않았으면 지난번에 센 값을 그대로 돌려준다. Redis를 못 읽으면(버전 null) 매번 센다 */
	public DealCounts counts() {
		String version = cacheVersion.current();
		Snapshot current = snapshot;
		if (version != null && current != null && version.equals(current.version())) {
			return current.counts();
		}
		DealCounts counts = jdbc.queryForObject("""
				SELECT (SELECT count(*) FROM sale_deal), (SELECT count(*) FROM rent_deal),
				       (SELECT count(*) FROM detached_deal)
				""", (rs, i) -> new DealCounts(rs.getLong(1), rs.getLong(2), rs.getLong(3), clock.instant()));
		snapshot = new Snapshot(version, counts);
		return counts;
	}

}
