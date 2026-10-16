package io.github.minunglee.jipdon.region;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 수집 대상 지역: 수도권(서울·인천·경기) 83개 시군구.
 *
 * 코드는 실거래가 API에 실제로 조회해 거래가 나오는 것을 확인했다 (2026-10).
 * 2026년 행정구역 개편으로 바뀐 곳: 인천 중구·동구 → 제물포구(28125)·영종구(28155), 인천 서구 → 서구(28275)·검단구(28290),
 * 화성시 → 만세구(41591)·효행구(41593)·병점구(41595)·동탄구(41597). 부천시는 구 단위(41192·41194·41196)로 조회해야 한다.
 */
public final class Regions {

	private static final String SEOUL = "서울";

	private static final String INCHEON = "인천";

	private static final String GYEONGGI = "경기";

	public static final List<Region> ALL = List.of(
			// 서울 25개 구
			new Region("11110", SEOUL, "종로구"), new Region("11140", SEOUL, "중구"), new Region("11170", SEOUL, "용산구"),
			new Region("11200", SEOUL, "성동구"), new Region("11215", SEOUL, "광진구"), new Region("11230", SEOUL, "동대문구"),
			new Region("11260", SEOUL, "중랑구"), new Region("11290", SEOUL, "성북구"), new Region("11305", SEOUL, "강북구"),
			new Region("11320", SEOUL, "도봉구"), new Region("11350", SEOUL, "노원구"), new Region("11380", SEOUL, "은평구"),
			new Region("11410", SEOUL, "서대문구"), new Region("11440", SEOUL, "마포구"), new Region("11470", SEOUL, "양천구"),
			new Region("11500", SEOUL, "강서구"), new Region("11530", SEOUL, "구로구"), new Region("11545", SEOUL, "금천구"),
			new Region("11560", SEOUL, "영등포구"), new Region("11590", SEOUL, "동작구"), new Region("11620", SEOUL, "관악구"),
			new Region("11650", SEOUL, "서초구"), new Region("11680", SEOUL, "강남구"), new Region("11710", SEOUL, "송파구"),
			new Region("11740", SEOUL, "강동구"),
			// 인천 (2026년 개편 반영)
			new Region("28125", INCHEON, "제물포구"), new Region("28155", INCHEON, "영종구"),
			new Region("28177", INCHEON, "미추홀구"), new Region("28185", INCHEON, "연수구"), new Region("28200", INCHEON, "남동구"),
			new Region("28237", INCHEON, "부평구"), new Region("28245", INCHEON, "계양구"), new Region("28275", INCHEON, "서구"),
			new Region("28290", INCHEON, "검단구"), new Region("28710", INCHEON, "강화군"), new Region("28720", INCHEON, "옹진군"),
			// 경기
			new Region("41111", GYEONGGI, "수원시 장안구"), new Region("41113", GYEONGGI, "수원시 권선구"),
			new Region("41115", GYEONGGI, "수원시 팔달구"), new Region("41117", GYEONGGI, "수원시 영통구"),
			new Region("41131", GYEONGGI, "성남시 수정구"), new Region("41133", GYEONGGI, "성남시 중원구"),
			new Region("41135", GYEONGGI, "성남시 분당구"), new Region("41150", GYEONGGI, "의정부시"),
			new Region("41171", GYEONGGI, "안양시 만안구"), new Region("41173", GYEONGGI, "안양시 동안구"),
			new Region("41192", GYEONGGI, "부천시 원미구"), new Region("41194", GYEONGGI, "부천시 소사구"),
			new Region("41196", GYEONGGI, "부천시 오정구"), new Region("41210", GYEONGGI, "광명시"),
			new Region("41220", GYEONGGI, "평택시"), new Region("41250", GYEONGGI, "동두천시"),
			new Region("41271", GYEONGGI, "안산시 상록구"), new Region("41273", GYEONGGI, "안산시 단원구"),
			new Region("41281", GYEONGGI, "고양시 덕양구"), new Region("41285", GYEONGGI, "고양시 일산동구"),
			new Region("41287", GYEONGGI, "고양시 일산서구"), new Region("41290", GYEONGGI, "과천시"),
			new Region("41310", GYEONGGI, "구리시"), new Region("41360", GYEONGGI, "남양주시"),
			new Region("41370", GYEONGGI, "오산시"), new Region("41390", GYEONGGI, "시흥시"),
			new Region("41410", GYEONGGI, "군포시"), new Region("41430", GYEONGGI, "의왕시"),
			new Region("41450", GYEONGGI, "하남시"), new Region("41461", GYEONGGI, "용인시 처인구"),
			new Region("41463", GYEONGGI, "용인시 기흥구"), new Region("41465", GYEONGGI, "용인시 수지구"),
			new Region("41480", GYEONGGI, "파주시"), new Region("41500", GYEONGGI, "이천시"),
			new Region("41550", GYEONGGI, "안성시"), new Region("41570", GYEONGGI, "김포시"),
			new Region("41591", GYEONGGI, "화성시 만세구"), new Region("41593", GYEONGGI, "화성시 효행구"),
			new Region("41595", GYEONGGI, "화성시 병점구"), new Region("41597", GYEONGGI, "화성시 동탄구"),
			new Region("41610", GYEONGGI, "광주시"), new Region("41630", GYEONGGI, "양주시"),
			new Region("41650", GYEONGGI, "포천시"), new Region("41670", GYEONGGI, "여주시"),
			new Region("41800", GYEONGGI, "연천군"), new Region("41820", GYEONGGI, "가평군"),
			new Region("41830", GYEONGGI, "양평군"));

	private static final Map<String, Region> BY_CODE = ALL.stream()
		.collect(Collectors.toUnmodifiableMap(Region::code, Function.identity()));

	private Regions() {
	}

	public static Optional<Region> find(String code) {
		return Optional.ofNullable(BY_CODE.get(code));
	}

}
