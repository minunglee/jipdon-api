package io.github.minunglee.jipdon.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * 건물 하나의 거래 목록으로 면적별 전세가율을 계산한다 (DB·캐시와 분리한 순수 계산).
 *
 * - 면적은 전용면적을 반올림한 ㎡ 단위로 묶는다 (84.99 → 85).
 * - 매매가·전세가는 중앙값을 쓴다. 한두 건의 특이한 거래(층·향·급매)에 평균보다 덜 흔들린다.
 * - 최근 12개월 거래로 계산하고, 그 기간에 거래가 없으면 24개월까지 넓힌다. 쓴 기간을 결과에 함께 남긴다.
 * - 전세가율 = 전세 중앙값 ÷ 매매 중앙값 × 100 (소수 첫째 자리 반올림)
 */
public final class JeonseCalculator {

	/** 전세가율 구간. 단정이 아니라 참고 지표다 */
	public enum Level {

		/** 70% 미만 */
		SAFE,
		/** 70% 이상 80% 미만 */
		CAUTION,
		/** 80% 이상: 집값이 조금만 내려도 보증금을 돌려받기 어려울 수 있음 */
		WARNING,
		/** 매매나 전세 거래가 없어 계산할 수 없음 */
		UNKNOWN;

		static Level of(BigDecimal ratio) {
			if (ratio == null) {
				return UNKNOWN;
			}
			if (ratio.compareTo(BigDecimal.valueOf(80)) >= 0) {
				return WARNING;
			}
			return ratio.compareTo(BigDecimal.valueOf(70)) >= 0 ? CAUTION : SAFE;
		}

	}

	/** 거래 한 건 (금액 단위 만원) */
	public record Deal(BigDecimal area, LocalDate date, int amount, Integer floor) {
	}

	/** 기간 안의 거래 통계 */
	public record Stat(int median, int count, int months, LocalDate latest, List<Deal> recent) {
	}

	/** 면적 하나의 결과 */
	public record AreaResult(int area, Stat sale, Stat jeonse, BigDecimal ratio, Level level) {
	}

	private static final int RECENT_DEALS = 5;

	private JeonseCalculator() {
	}

	public static List<AreaResult> calculate(List<Deal> sales, List<Deal> jeonse, LocalDate today, int recentMonths,
			int fallbackMonths) {
		Map<Integer, List<Deal>> salesByArea = byArea(sales);
		Map<Integer, List<Deal>> jeonseByArea = byArea(jeonse);
		TreeMap<Integer, Boolean> areas = new TreeMap<>();
		salesByArea.keySet().forEach(a -> areas.put(a, true));
		jeonseByArea.keySet().forEach(a -> areas.put(a, true));

		List<AreaResult> results = new ArrayList<>();
		for (int area : areas.keySet()) {
			Stat sale = stat(salesByArea.getOrDefault(area, List.of()), today, recentMonths, fallbackMonths);
			Stat lease = stat(jeonseByArea.getOrDefault(area, List.of()), today, recentMonths, fallbackMonths);
			BigDecimal ratio = (sale == null || lease == null) ? null
					: BigDecimal.valueOf(lease.median())
						.multiply(BigDecimal.valueOf(100))
						.divide(BigDecimal.valueOf(sale.median()), 1, RoundingMode.HALF_UP);
			results.add(new AreaResult(area, sale, lease, ratio, Level.of(ratio)));
		}
		return results;
	}

	static int areaBucket(BigDecimal area) {
		return area.setScale(0, RoundingMode.HALF_UP).intValueExact();
	}

	private static Map<Integer, List<Deal>> byArea(List<Deal> deals) {
		Map<Integer, List<Deal>> map = new TreeMap<>();
		for (Deal deal : deals) {
			map.computeIfAbsent(areaBucket(deal.area()), a -> new ArrayList<>()).add(deal);
		}
		return map;
	}

	/** 최근 기간에 거래가 없으면 더 긴 기간으로. 둘 다 없으면 null */
	private static Stat stat(List<Deal> deals, LocalDate today, int recentMonths, int fallbackMonths) {
		for (int months : new int[] { recentMonths, fallbackMonths }) {
			LocalDate from = today.minusMonths(months);
			Predicate<Deal> inWindow = d -> !d.date().isBefore(from) && !d.date().isAfter(today);
			List<Deal> window = deals.stream().filter(inWindow).toList();
			if (!window.isEmpty()) {
				List<Deal> newestFirst = window.stream()
					.sorted(Comparator.comparing(Deal::date).reversed())
					.toList();
				return new Stat(median(window), window.size(), months, newestFirst.get(0).date(),
						newestFirst.subList(0, Math.min(RECENT_DEALS, newestFirst.size())));
			}
		}
		return null;
	}

	/** 중앙값. 짝수 개면 가운데 두 값의 평균을 만원 단위로 반올림 */
	static int median(List<Deal> deals) {
		int[] amounts = deals.stream().mapToInt(Deal::amount).sorted().toArray();
		int mid = amounts.length / 2;
		if (amounts.length % 2 == 1) {
			return amounts[mid];
		}
		return (int) Math.round((amounts[mid - 1] + (long) amounts[mid]) / 2.0);
	}

}
