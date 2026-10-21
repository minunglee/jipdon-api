package io.github.minunglee.jipdon.collect;

import io.github.minunglee.jipdon.deal.BuildingKind;

/**
 * 수집하는 실거래가 API 8종. 공공데이터포털 서비스 이름과 오퍼레이션 이름을 함께 둔다.
 *
 * 단독·다가구(SH)는 개인정보 보호로 지번이 가려져(예: "1*") 오고 건물 이름이 없어 건물별로 묶을 수 없다.
 * 그래서 buildingKind가 없고, 동 단위 통계용으로 따로 저장한다.
 */
public enum DealKind {

	APT_SALE(BuildingKind.APT, true, "RTMSDataSvcAptTrade", "getRTMSDataSvcAptTrade"),
	APT_RENT(BuildingKind.APT, false, "RTMSDataSvcAptRent", "getRTMSDataSvcAptRent"),
	RH_SALE(BuildingKind.RH, true, "RTMSDataSvcRHTrade", "getRTMSDataSvcRHTrade"),
	RH_RENT(BuildingKind.RH, false, "RTMSDataSvcRHRent", "getRTMSDataSvcRHRent"),
	OFFI_SALE(BuildingKind.OFFI, true, "RTMSDataSvcOffiTrade", "getRTMSDataSvcOffiTrade"),
	OFFI_RENT(BuildingKind.OFFI, false, "RTMSDataSvcOffiRent", "getRTMSDataSvcOffiRent"),
	SH_SALE(null, true, "RTMSDataSvcSHTrade", "getRTMSDataSvcSHTrade"),
	SH_RENT(null, false, "RTMSDataSvcSHRent", "getRTMSDataSvcSHRent");

	private final BuildingKind buildingKind;

	private final boolean sale;

	private final String service;

	private final String operation;

	DealKind(BuildingKind buildingKind, boolean sale, String service, String operation) {
		this.buildingKind = buildingKind;
		this.sale = sale;
		this.service = service;
		this.operation = operation;
	}

	/** 건물별로 묶는 종류. 단독·다가구는 null */
	public BuildingKind buildingKind() {
		return buildingKind;
	}

	/** 단독·다가구: 건물을 특정할 수 없어 동 단위로만 쓴다 */
	public boolean detached() {
		return buildingKind == null;
	}

	/** 매매면 true, 전월세면 false */
	public boolean sale() {
		return sale;
	}

	public String path() {
		return "/" + service + "/" + operation;
	}

}
