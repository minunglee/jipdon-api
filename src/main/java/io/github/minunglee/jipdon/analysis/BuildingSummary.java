package io.github.minunglee.jipdon.analysis;

import io.github.minunglee.jipdon.deal.BuildingKind;

/** 검색 결과의 건물 한 줄 (최근 24개월 거래 수 포함) */
public record BuildingSummary(long id, BuildingKind kind, String regionCode, String regionName, String umd, String jibun,
		String name, String houseType, Integer buildYear, int saleCount, int jeonseCount) {

}
