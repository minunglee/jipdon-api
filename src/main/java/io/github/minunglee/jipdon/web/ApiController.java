package io.github.minunglee.jipdon.web;

import java.time.Duration;
import java.util.List;

import io.github.minunglee.jipdon.analysis.AnalysisService;
import io.github.minunglee.jipdon.analysis.BuildingAnalysis;
import io.github.minunglee.jipdon.analysis.BuildingSummary;
import io.github.minunglee.jipdon.deal.BuildingKind;
import io.github.minunglee.jipdon.region.Region;
import io.github.minunglee.jipdon.region.Regions;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * jipdon.com 화면이 부르는 조회 API. Nginx가 https://jipdon.com/api/ 를 이 앱으로 넘긴다.
 */
@RestController
@RequestMapping("/api")
class ApiController {

	private static final int MAX_QUERY_LENGTH = 30;

	private final AnalysisService analysis;

	ApiController(AnalysisService analysis) {
		this.analysis = analysis;
	}

	@GetMapping("/regions")
	ResponseEntity<List<Region>> regions() {
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1))).body(Regions.ALL);
	}

	@GetMapping("/buildings")
	List<BuildingSummary> buildings(@RequestParam String region, @RequestParam BuildingKind kind,
			@RequestParam(defaultValue = "") String q) {
		Region found = Regions.find(region).orElseThrow(() -> new BadRequestException("지원하지 않는 지역입니다."));
		if (q.length() > MAX_QUERY_LENGTH) {
			throw new BadRequestException("검색어는 " + MAX_QUERY_LENGTH + "자 이하로 입력해주세요.");
		}
		return analysis.search(found, kind, q);
	}

	@GetMapping("/buildings/{id}/jeonse")
	BuildingAnalysis jeonse(@PathVariable long id) {
		return analysis.analyze(id).orElseThrow(() -> new NotFoundException("건물을 찾을 수 없습니다."));
	}

	@GetMapping("/status")
	AnalysisService.Status status() {
		return analysis.status();
	}

}
