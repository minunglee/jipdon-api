package io.github.minunglee.jipdon.collect;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 API 응답(마포구 2026년 9월, 3건씩)을 저장해 둔 파일로 해석을 검증한다 */
public class RtmsXmlParserTest {

	public static String fixture(String name) {
		try (InputStream in = RtmsXmlParserTest.class.getResourceAsStream("/rtms/" + name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@Test
	void 아파트_매매를_읽는다() {
		RtmsPage page = RtmsXmlParser.parse(fixture("apt-trade.xml"), DealKind.APT_SALE);

		assertThat(page.totalCount()).isEqualTo(76);
		assertThat(page.items()).hasSize(3);
		RtmsItem item = page.items().get(0);
		assertThat(item.buildingName()).isEqualTo("현진에버빌2");
		assertThat(item.houseType()).isEqualTo("아파트");
		assertThat(item.sggCd()).isEqualTo("11440");
		assertThat(item.jibun()).isEqualTo("503");
		assertThat(item.price()).isEqualTo(108_000); // "108,000" 만원
		assertThat(item.exclusiveArea()).isEqualByComparingTo(new BigDecimal("83.654"));
		assertThat(item.floor()).isEqualTo(6);
		assertThat(item.dealDate()).isEqualTo(LocalDate.of(2026, 9, 18));
		assertThat(item.deposit()).isNull();
		assertThat(item.canceled()).isFalse();
	}

	@Test
	void 아파트_전월세를_읽는다_월세_0은_전세() {
		RtmsItem item = RtmsXmlParser.parse(fixture("apt-rent.xml"), DealKind.APT_RENT).items().get(0);

		assertThat(item.deposit()).isEqualTo(99_750);
		assertThat(item.monthlyRent()).isZero();
		assertThat(item.jeonse()).isTrue();
		assertThat(item.price()).isNull();
		assertThat(item.exclusiveArea()).isEqualByComparingTo(new BigDecimal("84.5978"));
		assertThat(item.dealDate()).isEqualTo(LocalDate.of(2026, 9, 29));
		assertThat(item.contractType()).isNull(); // 빈 값(" ")은 null
	}

	@Test
	void 연립다세대는_건물_이름과_주택_유형이_다른_태그로_온다() {
		RtmsItem sale = RtmsXmlParser.parse(fixture("rh-trade.xml"), DealKind.RH_SALE).items().get(0);
		assertThat(sale.houseType()).isEqualTo("다세대");
		assertThat(sale.buildingName()).isNotBlank();
		assertThat(sale.price()).isEqualTo(33_000);

		RtmsItem rent = RtmsXmlParser.parse(fixture("rh-rent.xml"), DealKind.RH_RENT).items().get(0);
		assertThat(rent.jibun()).isEqualTo("395-191");
		assertThat(rent.deposit()).isEqualTo(26_670);
	}

	@Test
	void 오피스텔은_offiNm을_건물_이름으로_읽는다() {
		RtmsItem sale = RtmsXmlParser.parse(fixture("offi-trade.xml"), DealKind.OFFI_SALE).items().get(0);
		assertThat(sale.buildingName()).isEqualTo("상암 한화 오벨리스크");
		assertThat(sale.houseType()).isEqualTo("오피스텔");
		assertThat(sale.jibun()).isEqualTo("1734");
		assertThat(sale.price()).isEqualTo(10_500);
		assertThat(sale.exclusiveArea()).isEqualByComparingTo(new BigDecimal("24.22"));

		RtmsItem rent = RtmsXmlParser.parse(fixture("offi-rent.xml"), DealKind.OFFI_RENT).items().get(0);
		assertThat(rent.deposit()).isEqualTo(19_000);
		assertThat(rent.monthlyRent()).isEqualTo(10);
		assertThat(rent.contractType()).isEqualTo("갱신");
	}

	@Test
	void 단독_다가구는_지번이_가려지고_이름_없이_연면적과_대지면적이_온다() {
		RtmsPage page = RtmsXmlParser.parse(fixture("sh-trade.xml"), DealKind.SH_SALE);
		RtmsItem first = page.items().get(0);
		assertThat(first.houseType()).isEqualTo("단독");
		assertThat(first.jibun()).isEqualTo("*");
		assertThat(first.buildingName()).isEmpty();
		assertThat(first.exclusiveArea()).isNull();
		assertThat(first.totalFloorArea()).isEqualByComparingTo(new BigDecimal("48.32"));
		assertThat(first.plottageArea()).isEqualByComparingTo(new BigDecimal("34"));
		assertThat(first.price()).isEqualTo(90_000);
		assertThat(page.items().get(2).houseType()).isEqualTo("다가구");

		RtmsItem rent = RtmsXmlParser.parse(fixture("sh-rent.xml"), DealKind.SH_RENT).items().get(0);
		assertThat(rent.houseType()).isEqualTo("다가구");
		assertThat(rent.jibun()).isEmpty(); // 전월세는 지번이 아예 없다
		assertThat(rent.totalFloorArea()).isEqualByComparingTo(new BigDecimal("16")); // 계약 면적
		assertThat(rent.deposit()).isEqualTo(1_000);
		assertThat(rent.monthlyRent()).isEqualTo(57);
	}

	@Test
	void 계약_해제된_매매를_표시한다() {
		String xml = fixture("apt-trade.xml").replaceFirst("<cdealType> </cdealType>", "<cdealType>O</cdealType>");
		assertThat(RtmsXmlParser.parse(xml, DealKind.APT_SALE).items().get(0).canceled()).isTrue();
	}

	@Test
	void 인증키_오류는_예외로_알린다() {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<OpenAPI_ServiceResponse><cmmMsgHeader>
				<errMsg>SERVICE ERROR</errMsg><returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
				<returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>""";
		assertThatThrownBy(() -> RtmsXmlParser.parse(xml, DealKind.APT_SALE)).isInstanceOfSatisfying(
				RtmsApiException.class, ex -> {
					assertThat(ex.code()).isEqualTo("30");
					assertThat(ex.quotaExceeded()).isFalse();
				});
	}

	@Test
	void 호출_한도_초과는_따로_구분한다() {
		String xml = """
				<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
				<returnAuthMsg>LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR</returnAuthMsg>
				<returnReasonCode>22</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>""";
		assertThatThrownBy(() -> RtmsXmlParser.parse(xml, DealKind.APT_RENT))
			.isInstanceOfSatisfying(RtmsApiException.class, ex -> assertThat(ex.quotaExceeded()).isTrue());
	}

	@Test
	void 외부_엔티티를_읽지_않는다() {
		String xml = """
				<?xml version="1.0"?><!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]>
				<response><header><resultCode>000</resultCode></header><body><items><item><aptNm>&x;</aptNm></item></items></body></response>""";
		assertThatThrownBy(() -> RtmsXmlParser.parse(xml, DealKind.APT_SALE)).isInstanceOf(RtmsApiException.class);
	}

	@Test
	void 포털의_인코딩된_키와_디코딩된_키를_같게_맞춘다() {
		assertThat(RtmsClient.normalizeKey("abc%2Bdef%3D%3D")).isEqualTo("abc+def==");
		assertThat(RtmsClient.normalizeKey(" abc+def== ")).isEqualTo("abc+def==");
		assertThat(RtmsClient.normalizeKey(null)).isEmpty();
	}

}
