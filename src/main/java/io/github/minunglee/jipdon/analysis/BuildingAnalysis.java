package io.github.minunglee.jipdon.analysis;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.github.minunglee.jipdon.analysis.JeonseCalculator.AreaResult;

/**
 * 건물 하나의 전세가율 분석 결과.
 *
 * @param baseDate 계산 기준일 (이날부터 거꾸로 12·24개월)
 * @param lastCollectedAt 실거래가를 마지막으로 받은 시각
 */
public record BuildingAnalysis(BuildingSummary building, List<AreaResult> areas, LocalDate baseDate,
		Instant lastCollectedAt) {

}
