package io.github.minunglee.jipdon.analysis;

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import io.github.minunglee.jipdon.JipdonProperties;
import io.github.minunglee.jipdon.analysis.JeonseCalculator.Deal;
import io.github.minunglee.jipdon.collect.CollectLogStore;
import io.github.minunglee.jipdon.deal.BuildingKind;
import io.github.minunglee.jipdon.region.Region;
import io.github.minunglee.jipdon.region.Regions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * 건물 검색과 전세가율 분석. 분석 결과는 Redis에 캐시하고, 새 거래가 수집되면 {@link CacheVersion}으로 한 번에 무효화한다.
 */
@Service
public class AnalysisService {

	private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

	/** 계산 방식이 바뀌면 올린다. 예전 방식으로 계산해 둔 캐시를 쓰지 않게 키에 넣는다 */
	private static final int CALCULATION_VERSION = 2;

	private static final int SEARCH_LIMIT = 20;

	/** 이름이 짧으면 후보가 많으므로, 거래 수를 셀 후보를 먼저 이만큼으로 줄인다 */
	private static final int SEARCH_CANDIDATES = 200;

	private final JdbcTemplate jdbc;

	private final StringRedisTemplate redis;

	private final JsonMapper json;

	private final CacheVersion cacheVersion;

	private final CollectLogStore collectLog;

	private final JipdonProperties.Analysis properties;

	private final Clock clock;

	AnalysisService(JdbcTemplate jdbc, StringRedisTemplate redis, JsonMapper json, CacheVersion cacheVersion,
			CollectLogStore collectLog, JipdonProperties properties, Clock clock) {
		this.jdbc = jdbc;
		this.redis = redis;
		this.json = json;
		this.cacheVersion = cacheVersion;
		this.collectLog = collectLog;
		this.properties = properties.analysis();
		this.clock = clock;
	}

	/** 지역 안에서 이름(또는 동)으로 건물을 찾는다. 최근 거래가 많은 순 */
	public List<BuildingSummary> search(Region region, BuildingKind kind, String query) {
		LocalDate since = LocalDate.now(clock).minusMonths(properties.fallbackMonths());
		String pattern = "%" + escapeLike(query == null ? "" : query.strip()) + "%";
		return jdbc.query("""
				SELECT * FROM (
				    SELECT b.*,
				           (SELECT count(*) FROM sale_deal s
				             WHERE s.building_id = b.id AND NOT s.canceled AND s.deal_date >= ?) AS sale_count,
				           (SELECT count(*) FROM rent_deal r
				             WHERE r.building_id = b.id AND r.monthly_rent_man = 0 AND r.deal_date >= ?) AS jeonse_count
				    FROM (SELECT * FROM building
				          WHERE sgg_cd = ? AND kind = ? AND (name ILIKE ? ESCAPE '\\' OR umd_nm ILIKE ? ESCAPE '\\')
				          LIMIT ?) b
				) found
				-- 출력 별칭은 식 안에서 쓸 수 없어 한 번 감싼다
				ORDER BY sale_count + jeonse_count DESC, name
				LIMIT ?
				""", summaryMapper(), Date.valueOf(since), Date.valueOf(since), region.code(), kind.name(), pattern,
				pattern, SEARCH_CANDIDATES, SEARCH_LIMIT);
	}

	public Optional<BuildingAnalysis> analyze(long buildingId) {
		String version = cacheVersion.current();
		LocalDate today = LocalDate.now(clock);
		String key = "jipdon:analysis:v" + version + ":c" + CALCULATION_VERSION + ":" + buildingId + ":" + today;
		if (version != null) {
			Optional<BuildingAnalysis> cached = readCache(key);
			if (cached.isPresent()) {
				return cached;
			}
		}
		Optional<BuildingAnalysis> result = compute(buildingId, today);
		if (version != null) {
			result.ifPresent(r -> writeCache(key, r));
		}
		return result;
	}

	private Optional<BuildingAnalysis> compute(long buildingId, LocalDate today) {
		LocalDate since = today.minusMonths(properties.fallbackMonths());
		List<BuildingSummary> found = jdbc.query("""
				SELECT b.*,
				       (SELECT count(*) FROM sale_deal s
				         WHERE s.building_id = b.id AND NOT s.canceled AND s.deal_date >= ?) AS sale_count,
				       (SELECT count(*) FROM rent_deal r
				         WHERE r.building_id = b.id AND r.monthly_rent_man = 0 AND r.deal_date >= ?) AS jeonse_count
				FROM building b WHERE b.id = ?
				""", summaryMapper(), Date.valueOf(since), Date.valueOf(since), buildingId);
		if (found.isEmpty()) {
			return Optional.empty();
		}
		RowMapper<Deal> deal = (rs, i) -> new Deal(rs.getBigDecimal("exclusive_area"),
				rs.getDate("deal_date").toLocalDate(), rs.getInt("amount"), (Integer) rs.getObject("floor"));
		List<Deal> sales = jdbc.query("""
				SELECT exclusive_area, deal_date, price_man AS amount, floor FROM sale_deal
				WHERE building_id = ? AND NOT canceled AND deal_date >= ?
				""", deal, buildingId, Date.valueOf(since));
		List<Deal> jeonse = jdbc.query("""
				SELECT exclusive_area, deal_date, deposit_man AS amount, floor FROM rent_deal
				WHERE building_id = ? AND monthly_rent_man = 0 AND deal_date >= ?
				""", deal, buildingId, Date.valueOf(since));
		return Optional.of(new BuildingAnalysis(found.get(0),
				JeonseCalculator.calculate(sales, jeonse, today, properties.recentMonths(), properties.fallbackMonths()),
				today, collectLog.lastCollectedAt()));
	}

	/** 화면 하단의 데이터 현황 */
	public record Status(Instant lastCollectedAt, long buildings, long saleDeals, long rentDeals, int regions) {
	}

	public Status status() {
		// 건수는 정확할 필요가 없어 통계 추정값을 쓴다 (수백만 줄을 매번 세지 않게)
		return new Status(collectLog.lastCollectedAt(), estimate("building"), estimate("sale_deal"),
				estimate("rent_deal"), Regions.ALL.size());
	}

	private long estimate(String table) {
		Long rows = jdbc.queryForObject(
				"SELECT GREATEST(reltuples, 0)::bigint FROM pg_class WHERE relname = ? AND relkind = 'r'", Long.class,
				table);
		return rows == null ? 0 : rows;
	}

	private RowMapper<BuildingSummary> summaryMapper() {
		return (rs, i) -> {
			String code = rs.getString("sgg_cd");
			return new BuildingSummary(rs.getLong("id"), BuildingKind.valueOf(rs.getString("kind")), code,
					Regions.find(code).map(Region::fullName).orElse(code), rs.getString("umd_nm"),
					rs.getString("jibun"), rs.getString("name"), rs.getString("house_type"),
					(Integer) rs.getObject("build_year"), rs.getInt("sale_count"), rs.getInt("jeonse_count"));
		};
	}

	private Optional<BuildingAnalysis> readCache(String key) {
		try {
			String value = redis.opsForValue().get(key);
			return value == null ? Optional.empty() : Optional.of(json.readValue(value, BuildingAnalysis.class));
		}
		catch (RuntimeException ex) {
			log.warn("분석 캐시를 읽지 못해 다시 계산합니다: {}", ex.getMessage());
			return Optional.empty();
		}
	}

	private void writeCache(String key, BuildingAnalysis analysis) {
		try {
			redis.opsForValue().set(key, json.writeValueAsString(analysis), properties.cacheTtl());
		}
		catch (RuntimeException ex) {
			log.warn("분석 캐시를 저장하지 못했습니다: {}", ex.getMessage());
		}
	}

	/** 검색어의 %, _ 를 글자 그대로 찾게 한다 */
	static String escapeLike(String value) {
		return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
