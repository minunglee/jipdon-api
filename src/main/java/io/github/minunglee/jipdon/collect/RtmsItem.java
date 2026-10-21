package io.github.minunglee.jipdon.collect;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 실거래가 API 응답의 거래 한 건. 금액 단위는 API와 같은 만원.
 *
 * @param price 매매가 (전월세면 null)
 * @param deposit 보증금 (매매면 null)
 * @param monthlyRent 월세, 전세면 0 (매매면 null)
 * @param canceled 계약 해제 신고가 된 매매
 * @param totalFloorArea 단독·다가구의 면적: 매매는 건물 연면적, 전월세는 계약 면적
 * @param plottageArea 단독·다가구 매매의 대지면적
 */
public record RtmsItem(String sggCd, String umd, String jibun, String buildingName, String houseType, Integer buildYear,
		BigDecimal exclusiveArea, Integer floor, LocalDate dealDate, Integer price, Integer deposit, Integer monthlyRent,
		String contractType, boolean canceled, BigDecimal totalFloorArea, BigDecimal plottageArea) {

	public boolean jeonse() {
		return monthlyRent != null && monthlyRent == 0;
	}

}
