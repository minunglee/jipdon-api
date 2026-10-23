package io.github.minunglee.jipdon.assist;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * AI가 고를 수 있는 계산기와 채울 수 있는 입력칸 목록.
 * <p>
 * 시스템 프롬프트도 이 목록으로 만들고, AI가 돌려준 값도 이 목록으로 검사한다. 목록에 없는 계산기·입력칸이나
 * 범위를 벗어난 값은 버려서, AI가 엉뚱한 주소나 값을 만들어도 화면에는 나가지 않는다.
 * 입력칸 key는 화면의 input id(라디오는 name)와 같다. 화면은 ?f.key=값 으로 받은 값을 채운다.
 */
final class AssistCatalog {

	enum Kind {

		/** 원 단위 금액 */
		MONEY,
		/** % 숫자 */
		PERCENT,
		/** 기간 등 일반 숫자 */
		NUMBER,
		/** 정해진 값 중 하나 */
		CHOICE

	}

	/**
	 * @param key 화면 입력칸 id (라디오는 name)
	 * @param label 사람이 읽는 이름
	 * @param choices CHOICE일 때 허용 값
	 */
	record Field(String key, String label, Kind kind, BigDecimal min, BigDecimal max, Set<String> choices) {

		static Field money(String key, String label) {
			return new Field(key, label, Kind.MONEY, BigDecimal.ZERO, new BigDecimal("100000000000"), Set.of());
		}

		static Field percent(String key, String label) {
			return new Field(key, label, Kind.PERCENT, BigDecimal.ZERO, new BigDecimal("100"), Set.of());
		}

		static Field number(String key, String label, int min, int max) {
			return new Field(key, label, Kind.NUMBER, BigDecimal.valueOf(min), BigDecimal.valueOf(max), Set.of());
		}

		static Field choice(String key, String label, String... choices) {
			return new Field(key, label, Kind.CHOICE, null, null, Set.of(choices));
		}

		/** 허용 범위의 값이면 화면에 넘길 문자열, 아니면 빈 값 */
		Optional<String> accept(Object raw) {
			if (raw == null) {
				return Optional.empty();
			}
			if (kind == Kind.CHOICE) {
				String value = raw.toString();
				return choices.contains(value) ? Optional.of(value) : Optional.empty();
			}
			BigDecimal value;
			try {
				value = new BigDecimal(raw.toString().replace(",", "").strip());
			}
			catch (NumberFormatException ex) {
				return Optional.empty();
			}
			if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
				return Optional.empty();
			}
			if (kind == Kind.MONEY) {
				value = value.setScale(0, java.math.RoundingMode.DOWN);
			}
			return Optional.of(value.stripTrailingZeros().toPlainString());
		}

		/** 화면에 보여줄 이름 (괄호 속 설명 제외) */
		String name() {
			int paren = label.indexOf('(');
			return paren < 0 ? label : label.substring(0, paren).strip();
		}

		/**
		 * 화면에 보여줄 값. 4억 → 400,000,000원, "기간(년)"의 5 → 5년, "방향(to-rent=보증금→월세, …)"의 to-rent → 보증금→월세
		 */
		String display(String value) {
			return switch (kind) {
				case MONEY -> new DecimalFormat("#,###").format(new BigDecimal(value)) + "원";
				case PERCENT -> value + "%";
				default -> {
					String note = label.contains("(") ? label.substring(label.indexOf('(') + 1, label.lastIndexOf(')'))
							: "";
					if (!note.contains("=")) {
						yield value + note; // 괄호 속이 단위
					}
					for (String option : note.split(",")) {
						String[] pair = option.strip().split("=", 2);
						if (pair.length == 2 && pair[0].equals(value)) {
							yield pair[1];
						}
					}
					yield value;
				}
			};
		}

	}

	record Tool(String id, String href, String name, String purpose, List<Field> fields) {

		Optional<Field> field(String key) {
			return fields.stream().filter(field -> field.key().equals(key)).findFirst();
		}

	}

	static final List<Tool> TOOLS = List.of(
			new Tool("jeonse-risk", "/jeonse-risk/", "전세 위험 확인 (전세가율)",
					"계약하려는 집의 매매가 대비 전세가율, 깡통전세 위험, 동네 전세 시세", List.of()),
			new Tool("jeonse-ranking", "/jeonse-ranking/", "전세가율 높은 동네 순위", "전국·시도별 전세가율 높은 동네 순위",
					List.of()),
			new Tool("brokerage-fee", "/brokerage-fee/", "중개보수(복비) 계산기", "매매·전세·월세 중개보수(복비) 법정 상한",
					List.of(Field.choice("deal", "거래 종류(sale=매매, lease=전월세)", "sale", "lease"),
							Field.money("price", "매매가"), Field.money("deposit", "보증금"),
							Field.money("rent", "월세"))),
			new Tool("rent-conversion", "/rent-conversion/", "전월세 전환 계산기",
					"보증금을 월세로, 월세를 보증금으로 바꿀 때 금액과 법정 상한 전환율",
					// 전환율은 법정 상한이 기본값이라 AI가 채우지 않게 목록에서 뺀다 (질문에 없는 율을 지어낸 적이 있음)
					List.of(Field.choice("mode", "방향(to-rent=보증금→월세, to-deposit=월세→보증금)", "to-rent",
							"to-deposit"), Field.money("deposit", "바꿀 보증금"), Field.money("rent", "바꿀 월세"))),
			new Tool("invest", "/invest/", "목표 금액 적립 계산기", "목표 금액을 모으려면 매달 얼마를 적립해야 하는지",
					List.of(Field.money("goal-target", "목표 금액"), Field.money("goal-initial", "지금 가진 돈"),
							Field.number("goal-years", "기간(년)", 1, 50), Field.percent("goal-rate", "연 수익률"))),
			new Tool("car-installment", "/car-installment/", "자동차 할부 계산기", "자동차 할부 월 납입금과 총 이자",
					List.of(Field.money("price", "차량 가격"), Field.money("down", "선수금"),
							Field.percent("rate", "할부 금리"),
							Field.choice("months", "할부 기간(개월)", "12", "24", "36", "48", "60", "72"))),
			new Tool("loan", "/loan/", "대출 상환 계산기", "주택담보대출·신용대출 월 상환액과 총 이자, 상환 방식 비교",
					List.of(Field.money("principal", "대출 금액"), Field.percent("rate", "연 금리"),
							Field.number("term", "기간(년)", 1, 50),
							Field.choice("method", "상환 방식(equal-payment=원리금균등, equal-principal=원금균등, bullet=만기일시)",
									"equal-payment", "equal-principal", "bullet"))),
			new Tool("dsr", "/dsr/", "DSR 계산기", "연소득 대비 원리금 비율(DSR)과 규제 안에서 빌릴 수 있는 최대 금액",
					List.of(Field.money("income", "연소득"), Field.money("loan", "새로 받을 대출"),
							Field.percent("rate", "연 금리"), Field.number("years", "기간(년)", 1, 50),
							Field.money("other", "기존 대출 연간 원리금(전세대출 제외)"))),
			new Tool("jeonse-loan", "/jeonse-loan/", "전세대출 이자 계산기", "전세 보증금과 대출 비율로 매달 내는 이자와 보증료",
					List.of(Field.money("deposit", "전세 보증금"), Field.percent("ratio", "대출 비율"),
							Field.percent("rate", "연 금리"), Field.number("years", "기간(년)", 1, 10))));

	static Optional<Tool> tool(String id) {
		return TOOLS.stream().filter(tool -> tool.id().equals(id)).findFirst();
	}

	/** 시스템 프롬프트에 넣을 계산기 설명 */
	static String describe() {
		StringBuilder text = new StringBuilder();
		for (Tool tool : TOOLS) {
			text.append("- ").append(tool.id()).append(": ").append(tool.purpose());
			if (!tool.fields().isEmpty()) {
				text.append(" / 입력칸: ");
				text.append(String.join(", ",
						tool.fields().stream().map(field -> field.key() + "(" + field.label() + ")").toList()));
			}
			text.append('\n');
		}
		return text.toString();
	}

	private AssistCatalog() {
	}

}
