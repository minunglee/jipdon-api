package io.github.minunglee.jipdon.visit;

import io.github.minunglee.jipdon.TestcontainersConfiguration;
import io.github.minunglee.jipdon.collect.RtmsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "jipdon.internal-token=test-internal-token")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VisitIntegrationTest {

	static final String CHROME = "Mozilla/5.0 (Windows NT 10.0) Chrome/130.0";

	static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0) Mobile/15E148 Safari/604.1";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	RtmsClient client;

	@BeforeEach
	void setUp() {
		jdbc.execute("TRUNCATE page_view");
	}

	ResultActions visit(String path, String ref, String ip, String ua) throws Exception {
		return mockMvc.perform(post("/api/pv").contentType(MediaType.APPLICATION_JSON)
			.content("{\"path\":\"" + path + "\",\"ref\":\"" + ref + "\"}")
			.header("User-Agent", ua)
			.with(r -> {
				r.setRemoteAddr(ip);
				return r;
			}));
	}

	@Test
	void 방문을_기록하되_원래_IP는_남기지_않는다() throws Exception {
		visit("/jeonse-risk/?region=11440#b1", "https://www.google.com/search?q=x", "203.0.113.7", CHROME)
			.andExpect(status().isNoContent());

		var row = jdbc.queryForMap("SELECT path, referrer_host, visitor, device FROM page_view");
		assertThat(row.get("path")).isEqualTo("/jeonse-risk/"); // 검색어·# 제거
		assertThat(row.get("referrer_host")).isEqualTo("www.google.com"); // 호스트만
		assertThat(row.get("device")).isEqualTo("desktop");
		assertThat((String) row.get("visitor")).hasSize(16).doesNotContain("203.0.113.7");
	}

	@Test
	void 로봇과_사이트_안_이동은_구분한다() throws Exception {
		visit("/", "", "203.0.113.7", "Googlebot/2.1 (+http://www.google.com/bot.html)").andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM page_view", Integer.class)).isZero();

		visit("/loan/", "https://jipdon.com/", "203.0.113.7", IPHONE);
		var row = jdbc.queryForMap("SELECT referrer_host, device FROM page_view");
		assertThat(row.get("referrer_host")).isNull();
		assertThat(row.get("device")).isEqualTo("mobile");
	}

	@Test
	void 통계는_내부_토큰이_있어야_보이고_순방문자는_날마다_센다() throws Exception {
		visit("/", "", "203.0.113.7", CHROME);
		visit("/loan/", "", "203.0.113.7", CHROME); // 같은 사람
		visit("/", "https://search.naver.com/", "198.51.100.9", IPHONE); // 다른 사람

		mockMvc.perform(get("/internal/stats")).andExpect(status().isNotFound());
		mockMvc.perform(get("/internal/stats").header("X-Internal-Token", "wrong")).andExpect(status().isNotFound());

		mockMvc.perform(get("/internal/stats").header("X-Internal-Token", "test-internal-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.today.views").value(3))
			.andExpect(jsonPath("$.today.visitors").value(2))
			.andExpect(jsonPath("$.last30.views").value(3))
			.andExpect(jsonPath("$.daily[0].date").value("2026-10-16"))
			.andExpect(jsonPath("$.topPages[0].key").value("/"))
			.andExpect(jsonPath("$.topPages[0].count").value(2))
			.andExpect(jsonPath("$.topReferrers[0].key").value("search.naver.com"))
			.andExpect(jsonPath("$.devices.length()").value(2));
	}

	@Test
	void 새로고침은_30분_안에_한_번만_센다() throws Exception {
		for (int i = 0; i < 5; i++) {
			visit("/jeonse-risk/", "", "203.0.113.7", CHROME);
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM page_view", Integer.class)).isEqualTo(1);
		visit("/loan/", "", "203.0.113.7", CHROME); // 다른 페이지는 따로
		visit("/jeonse-risk/", "", "198.51.100.9", CHROME); // 다른 사람은 따로
		assertThat(jdbc.queryForObject("SELECT count(*) FROM page_view", Integer.class)).isEqualTo(3);
	}

	@Test
	void 이상한_경로는_기록하지_않는다() throws Exception {
		visit("javascript:alert(1)", "", "203.0.113.7", CHROME).andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM page_view", Integer.class)).isZero();
	}

}
