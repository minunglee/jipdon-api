package io.github.minunglee.jipdon.collect;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import io.github.minunglee.jipdon.deal.BuildingKind;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 실거래가 API의 XML 응답을 읽는다.
 *
 * 정상 응답은 {@code <response><header><resultCode>}, 인증키 오류 등은 {@code <OpenAPI_ServiceResponse>} 형식으로 온다.
 * 빈 값은 공백 한 칸(" ")으로 오고, 금액은 "108,000"처럼 쉼표가 들어 있다(단위 만원).
 */
public final class RtmsXmlParser {

	/** 공공데이터포털 공통 오류 중 호출 한도 초과 */
	private static final String QUOTA_EXCEEDED_CODE = "22";

	private RtmsXmlParser() {
	}

	/** API 응답 XML → 거래 목록. 오류 응답이면 코드와 함께 예외 (호출 한도 초과 코드 22는 따로 표시) */
	public static RtmsPage parse(String xml, DealKind kind) {
		Document doc = read(xml);
		Element root = doc.getDocumentElement();
		if ("OpenAPI_ServiceResponse".equals(root.getTagName())) {
			String code = text(root, "returnReasonCode");
			String message = text(root, "errMsg");
			throw new RtmsApiException(code == null ? "?" : code, message == null ? "" : message,
					QUOTA_EXCEEDED_CODE.equals(code));
		}
		String resultCode = text(root, "resultCode");
		if (resultCode == null || !(resultCode.equals("000") || resultCode.equals("00"))) {
			String code = resultCode == null ? "?" : resultCode;
			throw new RtmsApiException(code, String.valueOf(text(root, "resultMsg")),
					QUOTA_EXCEEDED_CODE.equals(code) || "022".equals(code));
		}

		String total = text(root, "totalCount");
		NodeList nodes = root.getElementsByTagName("item");
		List<RtmsItem> items = new ArrayList<>(nodes.getLength());
		for (int i = 0; i < nodes.getLength(); i++) {
			items.add(item((Element) nodes.item(i), kind));
		}
		return new RtmsPage(total == null ? items.size() : Integer.parseInt(total), items);
	}

	/** item 태그 하나 → 거래 하나. 종류마다 건물 이름 태그가 달라(aptNm, offiNm, mhouseNm) 나눠 읽는다 */
	private static RtmsItem item(Element e, DealKind kind) {
		// 종류마다 건물 이름 태그가 다르다. 단독·다가구는 이름이 없다
		String name;
		String houseType;
		if (kind.detached()) {
			name = null;
			houseType = text(e, "houseType"); // 단독·다가구
		}
		else if (kind.buildingKind() == BuildingKind.APT) {
			name = text(e, "aptNm");
			houseType = "아파트";
		}
		else if (kind.buildingKind() == BuildingKind.OFFI) {
			name = text(e, "offiNm");
			houseType = "오피스텔";
		}
		else {
			name = text(e, "mhouseNm");
			houseType = text(e, "houseType"); // 연립·다세대
		}
		LocalDate date = LocalDate.of(integer(e, "dealYear"), integer(e, "dealMonth"), integer(e, "dealDay"));
		return new RtmsItem(text(e, "sggCd"), orEmpty(text(e, "umdNm")), orEmpty(text(e, "jibun")), orEmpty(name),
				houseType, integer(e, "buildYear"), decimal(e, "excluUseAr"), integer(e, "floor"), date,
				kind.sale() ? integer(e, "dealAmount") : null, kind.sale() ? null : integer(e, "deposit"),
				kind.sale() ? null : orZero(integer(e, "monthlyRent")), text(e, "contractType"),
				"O".equals(text(e, "cdealType")), decimal(e, "totalFloorAr"), decimal(e, "plottageAr"));
	}

	/** XML 문서 읽기. 외부 엔티티를 막아 XXE 공격을 차단한다 */
	private static Document read(String xml) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			// 외부 엔티티를 읽지 않는다 (XXE 방지)
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			DocumentBuilder builder = factory.newDocumentBuilder();
			return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new RtmsApiException("XML", "응답을 읽을 수 없습니다: " + ex.getMessage(), false);
		}
	}

	/** 태그의 글자. 없거나 공백뿐이면 null */
	private static String text(Element parent, String tag) {
		NodeList list = parent.getElementsByTagName(tag);
		if (list.getLength() == 0) {
			return null;
		}
		Node node = list.item(0);
		String value = node.getTextContent().strip();
		return value.isEmpty() ? null : value;
	}

	/** 태그 값을 정수로 ("12,345"처럼 쉼표가 있어도 읽음). 없으면 null */
	private static Integer integer(Element parent, String tag) {
		String value = text(parent, tag);
		return value == null ? null : Integer.valueOf(value.replace(",", ""));
	}

	/** 태그 값을 소수로 (면적 등). 없으면 null */
	private static BigDecimal decimal(Element parent, String tag) {
		String value = text(parent, tag);
		return value == null ? null : new BigDecimal(value);
	}

	/** null이면 빈 문자열 (건물 키에 null이 들어가지 않게) */
	private static String orEmpty(String value) {
		return value == null ? "" : value;
	}

	/** null이면 0 (월세가 비어 있으면 전세) */
	private static Integer orZero(Integer value) {
		return value == null ? 0 : value;
	}

}
