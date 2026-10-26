package io.github.minunglee.jipdon.analysis;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.minunglee.jipdon.JipdonProperties;
import io.github.minunglee.jipdon.deal.BuildingKind;
import io.github.minunglee.jipdon.region.Region;
import io.github.minunglee.jipdon.region.Regions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전세가율 높은 동네 순위.
 *
 * 1. 수집이 끝나면 건물·면적별 최근 12개월 매매·전세 중앙값과 전세가율을 area_ratio 표에 다시 계산해 둔다.
 * 2. 순위는 그 표를 동 단위로 묶어 전세가율의 중앙값으로 매긴다. 표본(건물·면적)이 3개 미만인 동은 빼서
 *    한두 건의 특이 거래가 맨 위에 오르지 않게 한다.
 * 3. 지분 거래·공공임대처럼 비정상적인 값(전세가율 20% 미만, 150% 초과)은 순위 계산에서 뺀다.
 */
@Service
public class RankingService {

	private static final Logger log = LoggerFactory.getLogger(RankingService.class);

	/** 동네 순위에 넣는 최소 표본 수 (건물·면적 3개 미만인 동은 거래 한두 건에 순위가 흔들려 뺀다) */
	static final int MIN_SAMPLES = 3;

	/** 순위에 보여줄 최대 동 수 */
	private static final int LIMIT = 50;

	private final JdbcTemplate jdbc;

	private final StringRedisTemplate redis;

	private final JsonMapper json;

	private final CacheVersion cacheVersion;

	private final JipdonProperties.Analysis properties;

	private final Clock clock;

	RankingService(JdbcTemplate jdbc, StringRedisTemplate redis, JsonMapper json, CacheVersion cacheVersion,
			JipdonProperties properties, Clock clock) {
		this.jdbc = jdbc;
		this.redis = redis;
		this.json = json;
		this.cacheVersion = cacheVersion;
		this.properties = properties.analysis();
		this.clock = clock;
	}

	/** 동 하나의 순위 행. ratio는 동 안 건물·면적별 전세가율의 중앙값 */
	public record Row(int rank, String regionCode, String regionName, String umd, double ratio, int samples,
			double warningShare, int saleCount, int jeonseCount) {
	}

	/**
	 * @param sido 시·도 이름 (null이면 전국)
	 * @param computedAt 요약을 계산한 시각, since 계산에 쓴 기간의 시작일
	 */
	public record Ranking(BuildingKind kind, String sido, int months, int minSamples, Instant computedAt,
			LocalDate since, List<Row> rows) {
	}

	/** 건물·면적별 전세가율 요약을 다시 계산한다 (수집 후 호출). 표를 통째로 바꾸므로 한 트랜잭션에서 한다 */
	@Transactional
	public int refresh() {
		long started = System.nanoTime();
		LocalDate since = LocalDate.now(clock).minusMonths(properties.recentMonths());
		jdbc.update("DELETE FROM area_ratio");
		int rows = jdbc.update("""
				INSERT INTO area_ratio (building_id, area, sale_median, sale_count, jeonse_median, jeonse_count, ratio)
				SELECT s.building_id, s.area, round(s.med), s.cnt, round(j.med), j.cnt, round((j.med * 100 / s.med)::numeric, 1)
				FROM (SELECT building_id, floor(exclusive_area)::int AS area,
				             percentile_cont(0.5) WITHIN GROUP (ORDER BY price_man) AS med, count(*) AS cnt
				      FROM sale_deal WHERE NOT canceled AND deal_date >= ? GROUP BY 1, 2) s
				JOIN (SELECT building_id, floor(exclusive_area)::int AS area,
				             percentile_cont(0.5) WITHIN GROUP (ORDER BY deposit_man) AS med, count(*) AS cnt
				      FROM rent_deal WHERE monthly_rent_man = 0 AND deal_date >= ? GROUP BY 1, 2) j
				  ON j.building_id = s.building_id AND j.area = s.area
				WHERE s.med > 0
				""", Date.valueOf(since), Date.valueOf(since));
		jdbc.update("""
				INSERT INTO area_ratio_meta (id, computed_at, since) VALUES (1, ?, ?)
				ON CONFLICT (id) DO UPDATE SET computed_at = EXCLUDED.computed_at, since = EXCLUDED.since
				""", Timestamp.from(clock.instant()), Date.valueOf(since));
		log.info("전세가율 요약 다시 계산: 건물·면적 {}개 ({}ms)", rows, (System.nanoTime() - started) / 1_000_000);
		return rows;
	}

	/** 요약이 비어 있으면 (처음 배포 직후) 계산해 둔다 */
	public void refreshIfEmpty() {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM area_ratio_meta", Integer.class);
		if (count == null || count == 0) {
			refresh();
		}
	}

	/** 전세가율 높은 동네 순위 (종류별, 시도 또는 전국). 요약 표(area_ratio)를 동 단위로 묶어 중앙값을 낸다. 결과는 버전 키로 캐시 */
	public Ranking ranking(BuildingKind kind, String sido) {
		String version = cacheVersion.current();
		String key = "jipdon:ranking:v" + version + ":" + kind + ":" + (sido == null ? "all" : sido);
		if (version != null) {
			Optional<Ranking> cached = readCache(key);
			if (cached.isPresent()) {
				return cached.get();
			}
		}
		List<Object> args = new ArrayList<>(List.of(kind.name()));
		String sidoFilter = "";
		if (sido != null) {
			List<String> codes = Regions.ALL.stream().filter(r -> r.sido().equals(sido)).map(Region::code).toList();
			sidoFilter = " AND b.sgg_cd IN (" + String.join(",", codes.stream().map(c -> "?").toList()) + ")";
			args.addAll(codes);
		}
		args.add(MIN_SAMPLES);
		args.add(LIMIT);
		List<Row> rows = new ArrayList<>();
		jdbc.query("""
				SELECT b.sgg_cd, b.umd_nm,
				       percentile_cont(0.5) WITHIN GROUP (ORDER BY a.ratio) AS ratio,
				       count(*) AS samples,
				       avg(CASE WHEN a.ratio >= 80 THEN 1.0 ELSE 0 END) AS warning_share,
				       sum(a.sale_count) AS sale_count, sum(a.jeonse_count) AS jeonse_count
				FROM area_ratio a JOIN building b ON b.id = a.building_id
				WHERE b.kind = ? AND a.ratio BETWEEN 20 AND 150""" + sidoFilter + """

				GROUP BY b.sgg_cd, b.umd_nm
				HAVING count(*) >= ?
				ORDER BY ratio DESC, samples DESC
				LIMIT ?
				""", rs -> {
			String code = rs.getString("sgg_cd");
			rows.add(new Row(rows.size() + 1, code, Regions.find(code).map(Region::fullName).orElse(code),
					rs.getString("umd_nm"), Math.round(rs.getDouble("ratio") * 10) / 10.0, rs.getInt("samples"),
					Math.round(rs.getDouble("warning_share") * 1000) / 10.0, rs.getInt("sale_count"),
					rs.getInt("jeonse_count")));
		}, args.toArray());

		Instant computedAt = null;
		LocalDate since = null;
		List<Object[]> meta = jdbc.query("SELECT computed_at, since FROM area_ratio_meta WHERE id = 1",
				(rs, i) -> new Object[] { rs.getTimestamp(1).toInstant(), rs.getDate(2).toLocalDate() });
		if (!meta.isEmpty()) {
			computedAt = (Instant) meta.get(0)[0];
			since = (LocalDate) meta.get(0)[1];
		}
		Ranking ranking = new Ranking(kind, sido, properties.recentMonths(), MIN_SAMPLES, computedAt, since, rows);
		if (version != null) {
			writeCache(key, ranking);
		}
		return ranking;
	}

	/** Redis 캐시 읽기 (실패하면 빈 값 → 다시 계산) */
	private Optional<Ranking> readCache(String key) {
		try {
			String value = redis.opsForValue().get(key);
			return value == null ? Optional.empty() : Optional.of(json.readValue(value, Ranking.class));
		}
		catch (RuntimeException ex) {
			log.warn("순위 캐시를 읽지 못해 다시 계산합니다: {}", ex.getMessage());
			return Optional.empty();
		}
	}

	/** Redis 캐시 저장 (실패해도 응답에는 영향 없음) */
	private void writeCache(String key, Ranking ranking) {
		try {
			redis.opsForValue().set(key, json.writeValueAsString(ranking), properties.cacheTtl());
		}
		catch (RuntimeException ex) {
			log.warn("순위 캐시를 저장하지 못했습니다: {}", ex.getMessage());
		}
	}

}
