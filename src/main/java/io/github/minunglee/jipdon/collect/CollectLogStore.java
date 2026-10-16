package io.github.minunglee.jipdon.collect;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 지역·종류·월 단위로 언제 수집했는지 기록한다 */
@Repository
public class CollectLogStore {

	private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

	private final JdbcTemplate jdbc;

	CollectLogStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record Task(String sggCd, DealKind kind, YearMonth month) {
	}

	/** 모든 수집 기록 (지역 83 × 종류 4 × 24개월 ≈ 8천 줄이라 한 번에 읽는다) */
	public Map<Task, Instant> all() {
		Map<Task, Instant> logs = new HashMap<>();
		jdbc.query("SELECT sgg_cd, deal_kind, deal_ym, collected_at FROM collect_log", rs -> {
			logs.put(new Task(rs.getString("sgg_cd"), DealKind.valueOf(rs.getString("deal_kind")),
					YearMonth.parse(rs.getString("deal_ym"), YYYYMM)), rs.getTimestamp("collected_at").toInstant());
		});
		return logs;
	}

	public void record(Task task, int rowCount, Instant at) {
		jdbc.update("""
				INSERT INTO collect_log (sgg_cd, deal_kind, deal_ym, row_count, collected_at) VALUES (?, ?, ?, ?, ?)
				ON CONFLICT (sgg_cd, deal_kind, deal_ym) DO UPDATE
				SET row_count = EXCLUDED.row_count, collected_at = EXCLUDED.collected_at
				""", task.sggCd(), task.kind().name(), task.month().format(YYYYMM), rowCount, Timestamp.from(at));
	}

	/** 가장 최근 수집 시각 (없으면 null) */
	public Instant lastCollectedAt() {
		Timestamp at = jdbc.queryForObject("SELECT max(collected_at) FROM collect_log", Timestamp.class);
		return at == null ? null : at.toInstant();
	}

}
