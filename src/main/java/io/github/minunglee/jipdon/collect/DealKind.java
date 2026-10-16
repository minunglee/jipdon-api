package io.github.minunglee.jipdon.collect;

import io.github.minunglee.jipdon.deal.BuildingKind;

/**
 * 수집하는 실거래가 API 4종. 공공데이터포털 서비스 이름과 오퍼레이션 이름을 함께 둔다.
 */
public enum DealKind {

	APT_SALE(BuildingKind.APT, true, "RTMSDataSvcAptTrade", "getRTMSDataSvcAptTrade"),
	APT_RENT(BuildingKind.APT, false, "RTMSDataSvcAptRent", "getRTMSDataSvcAptRent"),
	RH_SALE(BuildingKind.RH, true, "RTMSDataSvcRHTrade", "getRTMSDataSvcRHTrade"),
	RH_RENT(BuildingKind.RH, false, "RTMSDataSvcRHRent", "getRTMSDataSvcRHRent");

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

	public BuildingKind buildingKind() {
		return buildingKind;
	}

	/** 매매면 true, 전월세면 false */
	public boolean sale() {
		return sale;
	}

	public String path() {
		return "/" + service + "/" + operation;
	}

}
