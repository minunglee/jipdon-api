package io.github.minunglee.jipdon.web;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import io.github.minunglee.jipdon.TestcontainersConfiguration;
import io.github.minunglee.jipdon.collect.CollectLogStore;
import io.github.minunglee.jipdon.collect.Collector;
import io.github.minunglee.jipdon.collect.DealKind;
import io.github.minunglee.jipdon.collect.RtmsClient;
import io.github.minunglee.jipdon.collect.RtmsItem;
import io.github.minunglee.jipdon.collect.RtmsPage;
import io.github.minunglee.jipdon.collect.RtmsXmlParser;
import io.github.minunglee.jipdon.deal.DealStore;
import io.github.minunglee.jipdon.region.Regions;
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

import static io.github.minunglee.jipdon.collect.RtmsXmlParserTest.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 수집 → 저장 → 조회 API를 실제 PostgreSQL·Redis(Testcontainers)로 검증한다.
 * 공공 API는 부르지 않고, 저장해 둔 실제 응답(마포구 2026년 9월)을 돌려주게 바꿔 끼운다.
 */
@SpringBootTest(properties = { "jipdon.collect.history-months=2", "jipdon.collect.refresh-months=1",
		"jipdon.internal-token=test-internal-token" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JeonseApiIntegrationTest {

	static final YearMonth FIXTURE_MONTH = YearMonth.of(2026, 9);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	Collector collector;

	@Autowired
	DealStore dealStore;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	StringRedisTemplate redis;

	@MockitoBean
	RtmsClient client;

	@BeforeEach
	void setUp() {
		jdbc.execute("TRUNCATE area_ratio, area_ratio_meta, sale_deal, rent_deal, detached_deal, building, collect_log, collect_run RESTART IDENTITY");
		redis.getConnectionFactory().getConnection().serverCommands().flushDb();
		given(client.configured()).willReturn(true);
		given(client.pageSize()).willReturn(1000);
		given(client.fetch(any(), anyString(), any(), anyInt())).willReturn(new RtmsPage(0, List.of()));
		for (DealKind kind : DealKind.values()) {
			String file = switch (kind) {
				case APT_SALE -> "apt-trade.xml";
				case APT_RENT -> "apt-rent.xml";
				case RH_SALE -> "rh-trade.xml";
				case RH_RENT -> "rh-rent.xml";
				case OFFI_SALE -> "offi-trade.xml";
				case OFFI_RENT -> "offi-rent.xml";
				case SH_SALE -> "sh-trade.xml";
				case SH_RENT -> "sh-rent.xml";
			};
			given(client.fetch(kind, "11440", FIXTURE_MONTH, 1)).willReturn(RtmsXmlParser.parse(fixture(file), kind));
		}
	}

	@Test
	void 수집은_모든_지역_종류_월을_한_번씩_받고_다시_돌려도_중복되지_않는다() {
		Collector.Result first = collector.run();

		int tasks = Regions.ALL.size() * DealKind.values().length * 2; // 2개월
		assertThat(first.tasks()).isEqualTo(tasks);
		assertThat(first.calls()).isEqualTo(tasks);
		assertThat(first.changedRows()).isEqualTo(24); // 8종 × 3건
		assertThat(count("sale_deal")).isEqualTo(9); // 아파트·빌라·오피스텔
		assertThat(count("rent_deal")).isEqualTo(9);
		assertThat(count("detached_deal")).isEqualTo(6); // 단독·다가구는 건물 없이 따로
		assertThat(count("collect_log")).isEqualTo(tasks);

		// 20시간이 지나지 않았으므로 다시 받을 것이 없다
		Collector.Result second = collector.run();
		assertThat(second.calls()).isZero();
	}

	@Test
	void 수집_현황은_내부_토큰이_있어야_보이고_실행_이력을_남긴다() throws Exception {
		int tasks = Regions.ALL.size() * DealKind.values().length * 2;
		mockMvc.perform(get("/internal/collect").header("X-Internal-Token", "test-internal-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalTasks").value(tasks))
			.andExpect(jsonPath("$.missing").value(tasks / 2)) // 지난달은 아직 안 받음
			.andExpect(jsonPath("$.refreshDue").value(tasks / 2)) // 이번 달은 받을 차례
			.andExpect(jsonPath("$.recentRuns", hasSize(0)));

		collector.run();
		collector.run(); // 할 일이 없는 실행은 이력에 남기지 않는다

		mockMvc.perform(get("/internal/collect")).andExpect(status().isNotFound());
		mockMvc.perform(get("/internal/collect").header("X-Internal-Token", "test-internal-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.keyConfigured").value(true))
			.andExpect(jsonPath("$.lastCollectedAt").isNotEmpty())
			.andExpect(jsonPath("$.missing").value(0))
			.andExpect(jsonPath("$.refreshDue").value(0))
			.andExpect(jsonPath("$.recentRuns", hasSize(1)))
			.andExpect(jsonPath("$.recentRuns[0].calls").value(tasks))
			.andExpect(jsonPath("$.recentRuns[0].changedRows").value(24))
			.andExpect(jsonPath("$.recentRuns[0].stoppedByQuota").value(false));
	}

	@Test
	void 같은_거래를_다시_저장해도_한_줄이고_계약_해제만_반영한다() {
		List<RtmsItem> items = RtmsXmlParser.parse(fixture("apt-trade.xml"), DealKind.APT_SALE).items();
		var buildings = dealStore.loadBuildings(DealKind.APT_SALE.buildingKind(), "11440");

		assertThat(dealStore.save(DealKind.APT_SALE, "11440", items, buildings)).isEqualTo(3);
		assertThat(dealStore.save(DealKind.APT_SALE, "11440", items, buildings)).isZero();

		List<RtmsItem> canceled = RtmsXmlParser
			.parse(fixture("apt-trade.xml").replaceFirst("<cdealType> </cdealType>", "<cdealType>O</cdealType>"),
					DealKind.APT_SALE)
			.items();
		assertThat(dealStore.save(DealKind.APT_SALE, "11440", canceled, buildings)).isEqualTo(1);
		assertThat(count("sale_deal")).isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM sale_deal WHERE canceled", Integer.class)).isEqualTo(1);
	}

	@Test
	void 지역_목록() throws Exception {
		mockMvc.perform(get("/api/regions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(Regions.ALL.size())))
			.andExpect(jsonPath("$[0].code").value("11110"))
			.andExpect(jsonPath("$[0].sido").value("서울"));
	}

	@Test
	void 건물을_찾아_전세가율을_본다() throws Exception {
		collector.run();

		String body = mockMvc.perform(get("/api/buildings").param("region", "11440").param("kind", "APT").param("q", "에버빌"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("현진에버빌2"))
			.andExpect(jsonPath("$[0].regionName").value("서울 마포구"))
			.andExpect(jsonPath("$[0].saleCount").value(1))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long id = Long.parseLong(body.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));

		mockMvc.perform(get("/api/buildings/{id}/jeonse", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.building.name").value("현진에버빌2"))
			.andExpect(jsonPath("$.areas[0].area").value(83)) // 83.654㎡
			.andExpect(jsonPath("$.areas[0].sale.median").value(108_000))
			.andExpect(jsonPath("$.areas[0].sale.months").value(12))
			.andExpect(jsonPath("$.areas[0].level").value("UNKNOWN")) // 이 건물은 전세 거래가 없다
			.andExpect(jsonPath("$.baseDate").value("2026-10-16"));

		// 두 번째 조회는 캐시에서 읽는다
		assertThat(redis.keys("jipdon:analysis:*")).hasSize(1);
		mockMvc.perform(get("/api/buildings/{id}/jeonse", id)).andExpect(status().isOk());
	}

	@Test
	void 오피스텔도_건물로_찾는다() throws Exception {
		collector.run();
		mockMvc.perform(get("/api/buildings").param("region", "11440").param("kind", "OFFI").param("q", "오벨리스크"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].name").value("상암 한화 오벨리스크"))
			.andExpect(jsonPath("$[0].houseType").value("오피스텔"))
			.andExpect(jsonPath("$[0].saleCount").value(1));
	}

	@Test
	void 단독_다가구는_동별_시세로_본다() throws Exception {
		collector.run();
		// 다가구: 공덕동 매매 1건(13억 3천, 연면적 150㎡). 전월세 표본은 모두 월세라 전세 통계는 없다
		mockMvc.perform(get("/api/detached").param("region", "11440").param("type", "다가구"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.regionName").value("서울 마포구"))
			.andExpect(jsonPath("$.months").value(12))
			.andExpect(jsonPath("$.dongs", hasSize(1)))
			.andExpect(jsonPath("$.dongs[0].umd").value("공덕동"))
			.andExpect(jsonPath("$.dongs[0].saleCount").value(1))
			.andExpect(jsonPath("$.dongs[0].saleMedian").value(133_000))
			.andExpect(jsonPath("$.dongs[0].saleAreaMedian").value(150))
			.andExpect(jsonPath("$.dongs[0].jeonseCount").value(0))
			.andExpect(jsonPath("$.dongs[0].jeonseMedian").doesNotExist());
		// 단독: 공덕동·당인동 매매
		mockMvc.perform(get("/api/detached").param("region", "11440").param("type", "단독"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dongs", hasSize(2)));
		mockMvc.perform(get("/api/detached").param("region", "11440").param("type", "아파트"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void 단독_다가구_전세는_사분위로_범위를_보여준다() throws Exception {
		// 다가구 전세 4건: 1억, 1억 2천, 1억 5천, 2억 → 25%·50%·75% 지점 (선형 보간)
		for (int deposit : new int[] { 10_000, 12_000, 15_000, 20_000 }) {
			jdbc.update("INSERT INTO detached_deal (sgg_cd, umd_nm, house_type, deal_type, total_floor_area, deal_date,"
					+ " deposit_man, monthly_rent_man, natural_key) VALUES ('11440', '망원동', '다가구', 'RENT', 40,"
					+ " '2026-09-01', ?, 0, ?)", deposit, String.format("%032d", deposit));
		}
		mockMvc.perform(get("/api/detached").param("region", "11440").param("type", "다가구"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dongs[0].umd").value("망원동"))
			.andExpect(jsonPath("$.dongs[0].jeonseCount").value(4))
			.andExpect(jsonPath("$.dongs[0].jeonseP25").value(11_500))
			.andExpect(jsonPath("$.dongs[0].jeonseMedian").value(13_500))
			.andExpect(jsonPath("$.dongs[0].jeonseP75").value(16_250))
			.andExpect(jsonPath("$.dongs[0].jeonseAreaMedian").value(40));
	}

	@Test
	void 새_거래가_들어오면_캐시_버전이_바뀐다() {
		String before = redis.opsForValue().get("jipdon:cache:version");
		collector.run();
		assertThat(redis.opsForValue().get("jipdon:cache:version")).isNotEqualTo(before);
	}

	@Test
	void 잘못된_요청은_한국어_메시지로_답한다() throws Exception {
		mockMvc.perform(get("/api/buildings").param("region", "99999").param("kind", "APT"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("지원하지 않는 지역입니다."));
		mockMvc.perform(get("/api/buildings").param("region", "11440").param("kind", "HOUSE"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/buildings/{id}/jeonse", 999_999)).andExpect(status().isNotFound());
	}

	@Test
	void 검색어의_특수문자는_글자_그대로_찾는다() throws Exception {
		collector.run();
		mockMvc.perform(get("/api/buildings").param("region", "11440").param("kind", "APT").param("q", "%"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void 데이터_현황() throws Exception {
		collector.run();
		jdbc.execute("ANALYZE");
		mockMvc.perform(get("/api/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.regions").value(Regions.ALL.size()))
			.andExpect(jsonPath("$.lastCollectedAt").exists());
	}

	private int count(String table) {
		Map<String, Object> row = jdbc.queryForMap("SELECT count(*) AS c FROM " + table);
		return ((Number) row.get("c")).intValue();
	}

}
