package io.github.minunglee.jipdon.collect;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 수집 배치 실행 이력 */
@Repository
public class CollectRunStore {

	private final JdbcTemplate jdbc;

	CollectRunStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** 실행 이력 한 줄 (값의 뜻은 Collector.Result와 같다) */
	public record Run(Instant startedAt, Instant finishedAt, int tasks, int calls, int changedRows, int failedTasks,
			boolean stoppedByQuota, int remainingTasks) {
	}

	/** 실행 결과 한 줄 저장 */
	void record(Collector.Result result, Instant startedAt, Instant finishedAt) {
		jdbc.update("""
				INSERT INTO collect_run (started_at, finished_at, tasks, calls, changed_rows, failed_tasks,
				                         stopped_by_quota, remaining_tasks)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""", Timestamp.from(startedAt), Timestamp.from(finishedAt), result.tasks(), result.calls(),
				result.changedRows(), result.failedTasks(), result.stoppedByQuota(), result.remainingTasks());
	}

	/** 최근 실행부터 limit개 */
	public List<Run> recent(int limit) {
		return jdbc.query("SELECT * FROM collect_run ORDER BY started_at DESC LIMIT ?",
				(rs, i) -> new Run(rs.getTimestamp("started_at").toInstant(), rs.getTimestamp("finished_at").toInstant(),
						rs.getInt("tasks"), rs.getInt("calls"), rs.getInt("changed_rows"), rs.getInt("failed_tasks"),
						rs.getBoolean("stopped_by_quota"), rs.getInt("remaining_tasks")),
				limit);
	}

}
