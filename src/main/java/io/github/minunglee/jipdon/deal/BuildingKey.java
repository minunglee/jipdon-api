package io.github.minunglee.jipdon.deal;

/** 건물을 구분하는 값. 실거래가 API에 고유 번호가 없어 지역·동·지번·이름을 함께 쓴다 */
public record BuildingKey(BuildingKind kind, String sggCd, String umd, String jibun, String name) {

}
