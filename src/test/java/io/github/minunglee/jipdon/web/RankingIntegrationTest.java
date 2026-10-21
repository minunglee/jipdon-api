package io.github.minunglee.jipdon.web;

import java.util.concurrent.atomic.AtomicInteger;

import io.github.minunglee.jipdon.TestcontainersConfiguration;
import io.github.minunglee.jipdon.analysis.RankingService;
import io.github.minunglee.jipdon.collect.RtmsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 전세가율 동네 순위: 요약 계산 → 동 단위 중앙값 → 표본 기준·이상값 제외·시도 필터 (기준일 2026-10-16) */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RankingIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	StringRedisTemplate redis;

	@Autowired
	RankingService rankingService;

	@MockitoBean
	RtmsClient client; // 순위 테스트는 수집하지 않는다

	final AtomicInteger seq = new AtomicInteger();

	@BeforeEach
	void setUp() {
		jdbc.execute("TRUNCATE area_ratio, area_ratio_meta, sale_deal, rent_deal, detached_deal, building, collect_log RESTART IDENTITY CASCADE");
		redis.getConnectionFactory().getConnection().serverCommands().flushDb();
	}

	/** 건물 하나에 84㎡ 매매 1건과 전세 1건을 넣어 전세가율 ratio%를 만든다 */
	void building(String sgg, String umd, String kind, int ratio) {
		int n = seq.incrementAndGet();
		Long id = jdbc.queryForObject("INSERT INTO building (kind, sgg_cd, umd_nm, jibun, name) VALUES (?, ?, ?, ?, ?) RETURNING id",
				Long.class, kind, sgg, umd, String.valueOf(n), "건물" + n);
		jdbc.update("INSERT INTO sale_deal (building_id, exclusive_area, deal_date, price_man, natural_key) VALUES (?, 84.9, '2026-08-01', 10000, ?)",
				id, String.format("s%031d", n));
		jdbc.update("INSERT INTO rent_deal (building_id, exclusive_area, deal_date, deposit_man, monthly_rent_man, natural_key) VALUES (?, 84.2, '2026-09-01', ?, 0, ?)",
				id, ratio * 100, String.format("r%031d", n));
	}

	@Test
	void 동별_전세가율_중앙값이_높은_순서() throws Exception {
		// 망원동 (서울 마포구): 85·90·95 → 중앙값 90, 80% 이상 3/3
		building("11440", "망원동", "APT", 85);
		building("11440", "망원동", "APT", 90);
		building("11440", "망원동", "APT", 95);
		// 연산동 (부산 연제구): 60·70·75·80 → 중앙값 72.5, 80% 이상 1/4
		for (int r : new int[] { 60, 70, 75, 80 }) {
			building("26470", "연산동", "APT", r);
		}
		// 표본 2개뿐인 동은 빠진다
		building("11440", "합정동", "APT", 99);
		building("11440", "합정동", "APT", 99);
		// 지분 거래 같은 이상값(150% 초과)은 계산에서 빠진다 → 연산동 표본은 여전히 4개
		building("26470", "연산동", "APT", 300);
		// 다른 종류는 섞이지 않는다
		building("11440", "망원동", "RH", 10);

		assertThat(rankingService.refresh()).isEqualTo(11); // 3 + 4 + 2 + 1 + 1

		mockMvc.perform(get("/api/rankings").param("kind", "APT"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows", hasSize(2)))
			.andExpect(jsonPath("$.rows[0].rank").value(1))
			.andExpect(jsonPath("$.rows[0].umd").value("망원동"))
			.andExpect(jsonPath("$.rows[0].regionName").value("서울 마포구"))
			.andExpect(jsonPath("$.rows[0].ratio").value(90.0))
			.andExpect(jsonPath("$.rows[0].samples").value(3))
			.andExpect(jsonPath("$.rows[0].warningShare").value(100.0))
			.andExpect(jsonPath("$.rows[1].umd").value("연산동"))
			.andExpect(jsonPath("$.rows[1].ratio").value(72.5))
			.andExpect(jsonPath("$.rows[1].samples").value(4))
			.andExpect(jsonPath("$.rows[1].warningShare").value(25.0))
			.andExpect(jsonPath("$.minSamples").value(3))
			.andExpect(jsonPath("$.since").value("2025-10-16"))
			.andExpect(jsonPath("$.computedAt").exists());

		// 시·도로 좁히기
		mockMvc.perform(get("/api/rankings").param("kind", "APT").param("sido", "부산"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rows", hasSize(1)))
			.andExpect(jsonPath("$.rows[0].umd").value("연산동"));
	}

	@Test
	void 기간_밖의_거래는_요약에_넣지_않는다() {
		Long id = jdbc.queryForObject("INSERT INTO building (kind, sgg_cd, umd_nm, jibun, name) VALUES ('APT', '11440', '망원동', '1', '옛건물') RETURNING id", Long.class);
		jdbc.update("INSERT INTO sale_deal (building_id, exclusive_area, deal_date, price_man, natural_key) VALUES (?, 84, '2025-01-01', 10000, 'old0000000000000000000000000000s')", id);
		jdbc.update("INSERT INTO rent_deal (building_id, exclusive_area, deal_date, deposit_man, monthly_rent_man, natural_key) VALUES (?, 84, '2026-09-01', 9000, 0, 'old0000000000000000000000000000r')", id);
		assertThat(rankingService.refresh()).isZero();
	}

	@Test
	void 처음_한_번만_자동으로_계산한다() {
		building("11440", "망원동", "APT", 85);
		rankingService.refreshIfEmpty();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM area_ratio", Integer.class)).isEqualTo(1);
		building("11440", "망원동", "APT", 90);
		rankingService.refreshIfEmpty(); // 이미 계산한 적이 있으면 건너뛴다
		assertThat(jdbc.queryForObject("SELECT count(*) FROM area_ratio", Integer.class)).isEqualTo(1);
	}

	@Test
	void 없는_시도는_거부한다() throws Exception {
		mockMvc.perform(get("/api/rankings").param("sido", "평양")).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/rankings").param("kind", "HOUSE")).andExpect(status().isBadRequest());
	}

}
