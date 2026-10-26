package io.github.minunglee.jipdon.visit;

import java.time.LocalDate;
import java.util.List;

/**
 * 방문 통계 (관리자 화면용). minunglee.cloud 관리자 페이지가 내부망으로 읽어 간다.
 *
 * @param today 오늘, last7 최근 7일, last30 최근 30일 (오늘 포함)
 */
public record VisitStats(int days, Count today, Count last7, Count last30, List<Day> daily, List<Rank> topPages,
		List<Rank> topReferrers, List<Rank> devices) {

	/** 조회 수와 순방문자 수 (순방문자는 날짜마다 따로 센 값의 합) */
	public record Count(long views, long visitors) {
	}

	/** 하루 조회 수와 순방문자 수 */
	public record Day(LocalDate date, long views, long visitors) {
	}

	/** 순위 한 줄 (값, 횟수) */
	public record Rank(String key, long count) {
	}

}
