package io.github.minunglee.jipdon.web;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import io.github.minunglee.jipdon.analysis.AnalysisService;
import io.github.minunglee.jipdon.analysis.BuildingAnalysis;
import io.github.minunglee.jipdon.analysis.BuildingSummary;
import io.github.minunglee.jipdon.analysis.DetachedSummary;
import io.github.minunglee.jipdon.analysis.RankingService;
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

	/** 건물 검색어 최대 글자 수 (단지 이름 검색에 충분하고, 긴 LIKE 검색으로 DB를 괴롭히지 않게) */
	private static final int MAX_QUERY_LENGTH = 30;

	/** 단독·다가구 조회에서 받는 주택 유형 */
	private static final Set<String> DETACHED_TYPES = Set.of("다가구", "단독");

	private final AnalysisService analysis;

	private final RankingService ranking;

	ApiController(AnalysisService analysis, RankingService ranking) {
		this.analysis = analysis;
		this.ranking = ranking;
	}

	/** GET /api/regions: 지원 지역 목록 (거의 안 바뀌어 브라우저에 1시간 캐시) */
	@GetMapping("/regions")
	ResponseEntity<List<Region>> regions() {
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1))).body(Regions.ALL);
	}

	/** GET /api/buildings: 지역 안에서 건물 이름·동으로 검색 (최근 거래 많은 순) */
	@GetMapping("/buildings")
	List<BuildingSummary> buildings(@RequestParam String region, @RequestParam BuildingKind kind,
			@RequestParam(defaultValue = "") String q) {
		Region found = Regions.find(region).orElseThrow(() -> new BadRequestException("지원하지 않는 지역입니다."));
		if (q.length() > MAX_QUERY_LENGTH) {
			throw new BadRequestException("검색어는 " + MAX_QUERY_LENGTH + "자 이하로 입력해주세요.");
		}
		return analysis.search(found, kind, q);
	}

	/** GET /api/buildings/{id}/jeonse: 건물의 면적별 전세가율 분석 */
	@GetMapping("/buildings/{id}/jeonse")
	BuildingAnalysis jeonse(@PathVariable long id) {
		return analysis.analyze(id).orElseThrow(() -> new NotFoundException("건물을 찾을 수 없습니다."));
	}

	/** 단독·다가구 동별 시세 참고. type: 다가구 또는 단독 */
	@GetMapping("/detached")
	DetachedSummary detached(@RequestParam String region, @RequestParam(defaultValue = "다가구") String type) {
		Region found = Regions.find(region).orElseThrow(() -> new BadRequestException("지원하지 않는 지역입니다."));
		if (!DETACHED_TYPES.contains(type)) {
			throw new BadRequestException("주택 유형은 다가구 또는 단독만 지원합니다.");
		}
		return analysis.detached(found, type);
	}

	/** 전세가율 높은 동네 순위. sido를 비우면 전국 */
	@GetMapping("/rankings")
	RankingService.Ranking rankings(@RequestParam(defaultValue = "APT") BuildingKind kind,
			@RequestParam(required = false) String sido) {
		if (sido != null && !sido.isBlank() && Regions.ALL.stream().noneMatch(r -> r.sido().equals(sido))) {
			throw new BadRequestException("지원하지 않는 시·도입니다.");
		}
		return ranking.ranking(kind, sido == null || sido.isBlank() ? null : sido);
	}

	/** GET /api/status: 데이터 현황 (화면의 "실거래 데이터 … 갱신" 한 줄) */
	@GetMapping("/status")
	AnalysisService.Status status() {
		return analysis.status();
	}

}
