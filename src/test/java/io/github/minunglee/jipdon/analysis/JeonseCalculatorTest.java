package io.github.minunglee.jipdon.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.github.minunglee.jipdon.analysis.JeonseCalculator.AreaResult;
import io.github.minunglee.jipdon.analysis.JeonseCalculator.Deal;
import io.github.minunglee.jipdon.analysis.JeonseCalculator.Level;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JeonseCalculatorTest {

	static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

	static Deal deal(String area, String date, int amount) {
		return new Deal(new BigDecimal(area), LocalDate.parse(date), amount, 5);
	}

	static List<AreaResult> calc(List<Deal> sales, List<Deal> jeonse) {
		return JeonseCalculator.calculate(sales, jeonse, TODAY, 12, 24);
	}

	@Test
	void 중앙값은_홀수면_가운데_짝수면_가운데_둘의_평균() {
		assertThat(JeonseCalculator.median(List.of(deal("84", "2026-01-01", 3), deal("84", "2026-01-01", 1),
				deal("84", "2026-01-01", 100)))).isEqualTo(3);
		assertThat(JeonseCalculator.median(List.of(deal("84", "2026-01-01", 1), deal("84", "2026-01-01", 2))))
			.isEqualTo(2); // 1.5 → 반올림
	}

	@Test
	void 전용면적은_반올림한_제곱미터로_묶는다() {
		assertThat(JeonseCalculator.areaBucket(new BigDecimal("84.99"))).isEqualTo(85);
		assertThat(JeonseCalculator.areaBucket(new BigDecimal("84.5"))).isEqualTo(85);
		assertThat(JeonseCalculator.areaBucket(new BigDecimal("59.49"))).isEqualTo(59);
	}

	@Test
	void 전세가율은_전세_중앙값_나누기_매매_중앙값() {
		List<AreaResult> result = calc(
				List.of(deal("84.97", "2026-09-01", 100_000), deal("84.97", "2026-08-01", 110_000),
						deal("84.6", "2026-07-01", 90_000)),
				List.of(deal("84.97", "2026-09-10", 60_000), deal("84.97", "2026-05-01", 62_000)));

		assertThat(result).hasSize(1);
		AreaResult area = result.get(0);
		assertThat(area.area()).isEqualTo(85);
		assertThat(area.sale().median()).isEqualTo(100_000);
		assertThat(area.sale().count()).isEqualTo(3);
		assertThat(area.sale().latest()).isEqualTo(LocalDate.of(2026, 9, 1));
		assertThat(area.jeonse().median()).isEqualTo(61_000);
		assertThat(area.ratio()).isEqualByComparingTo("61.0");
		assertThat(area.level()).isEqualTo(Level.SAFE);
	}

	@Test
	void 구간은_70과_80을_기준으로_나눈다() {
		assertThat(Level.of(new BigDecimal("69.9"))).isEqualTo(Level.SAFE);
		assertThat(Level.of(new BigDecimal("70.0"))).isEqualTo(Level.CAUTION);
		assertThat(Level.of(new BigDecimal("79.9"))).isEqualTo(Level.CAUTION);
		assertThat(Level.of(new BigDecimal("80.0"))).isEqualTo(Level.WARNING);
		assertThat(Level.of(null)).isEqualTo(Level.UNKNOWN);
	}

	@Test
	void 최근_12개월에_거래가_없으면_24개월까지_넓히고_쓴_기간을_남긴다() {
		AreaResult area = calc(List.of(deal("59", "2025-03-01", 50_000)), List.of(deal("59", "2026-09-01", 42_000)))
			.get(0);

		assertThat(area.sale().months()).isEqualTo(24);
		assertThat(area.jeonse().months()).isEqualTo(12);
		assertThat(area.ratio()).isEqualByComparingTo("84.0");
		assertThat(area.level()).isEqualTo(Level.WARNING);
	}

	@Test
	void 매매가_없으면_전세가율을_계산하지_않는다() {
		AreaResult area = calc(List.of(), List.of(deal("39", "2026-09-01", 20_000))).get(0);

		assertThat(area.sale()).isNull();
		assertThat(area.ratio()).isNull();
		assertThat(area.level()).isEqualTo(Level.UNKNOWN);
	}

	@Test
	void 기간_밖의_거래는_쓰지_않는다() {
		assertThat(calc(List.of(deal("84", "2024-09-01", 1)), List.of(deal("84", "2024-01-01", 1))).get(0).sale())
			.isNull();
	}

	@Test
	void 최근_거래는_최신순으로_5건까지() {
		List<Deal> sales = List.of(deal("84", "2026-01-01", 1), deal("84", "2026-02-01", 2), deal("84", "2026-03-01", 3),
				deal("84", "2026-04-01", 4), deal("84", "2026-05-01", 5), deal("84", "2026-06-01", 6));
		List<Deal> recent = calc(sales, List.of()).get(0).sale().recent();

		assertThat(recent).hasSize(5);
		assertThat(recent.get(0).amount()).isEqualTo(6);
	}

}
