package io.github.minunglee.jipdon.region;

/**
 * 실거래가 API의 지역 단위(시군구). code는 법정동코드 앞 5자리(LAWD_CD).
 */
public record Region(String code, String sido, String name) {

	/** "서울 마포구"처럼 시도와 이름을 붙인 표시용 이름 */
	public String fullName() {
		return sido + " " + name;
	}

}
