package io.github.minunglee.jipdon.analysis;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 단독·다가구 동 단위 시세 참고. 건물을 특정할 수 없어 전세가율 대신 동네 시세 범위를 보여준다.
 *
 * @param months 통계에 쓴 기간 (기준일부터 거꾸로)
 */
public record DetachedSummary(String regionCode, String regionName, String houseType, int months, LocalDate baseDate,
		Instant lastCollectedAt, List<Dong> dongs) {

	/**
	 * 동 하나의 통계 (금액 단위 만원, 면적 ㎡). 거래가 없으면 해당 값은 null.
	 *
	 * @param jeonseAreaMedian 전세 계약 면적 중앙값
	 * @param saleAreaMedian 매매 건물 연면적 중앙값
	 */
	public record Dong(String umd, int jeonseCount, Integer jeonseP25, Integer jeonseMedian, Integer jeonseP75,
			Integer jeonseAreaMedian, int saleCount, Integer saleMedian, Integer saleAreaMedian) {
	}

}
