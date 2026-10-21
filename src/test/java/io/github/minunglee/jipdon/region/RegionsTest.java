package io.github.minunglee.jipdon.region;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegionsTest {

	@Test
	void 전국_17개_시도_256개_시군구() {
		assertThat(Regions.ALL).hasSize(256);
		Map<String, Long> bySido = Regions.ALL.stream()
			.collect(Collectors.groupingBy(Region::sido, Collectors.counting()));
		assertThat(bySido).hasSize(17).containsEntry("서울", 25L).containsEntry("경기", 47L).containsEntry("세종", 1L);
	}

	@Test
	void 코드는_5자리이고_겹치지_않는다() {
		assertThat(Regions.ALL).allSatisfy(r -> assertThat(r.code()).matches("\\d{5}"));
		assertThat(Regions.ALL.stream().map(Region::code).distinct().count()).isEqualTo(Regions.ALL.size());
	}

	@Test
	void 개편으로_바뀐_코드를_쓴다() {
		// 광주·전남 통합(2026-07): 예전 29xxx·46xxx 대신 12xxx
		assertThat(Regions.find("12210")).get().satisfies(r -> assertThat(r.fullName()).isEqualTo("광주 동구"));
		assertThat(Regions.find("12110")).get().satisfies(r -> assertThat(r.fullName()).isEqualTo("전남 목포시"));
		assertThat(Regions.find("29110")).isEmpty();
		assertThat(Regions.find("46110")).isEmpty();
		// 강원·전북 특별자치도, 대구 군위군
		assertThat(Regions.find("51110")).get().satisfies(r -> assertThat(r.sido()).isEqualTo("강원"));
		assertThat(Regions.find("52111")).get().satisfies(r -> assertThat(r.sido()).isEqualTo("전북"));
		assertThat(Regions.find("27720")).get().satisfies(r -> assertThat(r.fullName()).isEqualTo("대구 군위군"));
	}

}
