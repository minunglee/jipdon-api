package io.github.minunglee.jipdon.region;

/**
 * 실거래가 API의 지역 단위(시군구). code는 법정동코드 앞 5자리(LAWD_CD).
 */
public record Region(String code, String sido, String name) {

	public String fullName() {
		return sido + " " + name;
	}

}
