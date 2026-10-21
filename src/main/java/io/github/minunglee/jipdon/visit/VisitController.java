package io.github.minunglee.jipdon.visit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 방문 기록 받기(공개)와 통계 내주기(내부 전용).
 *
 * /internal 은 Nginx가 바깥으로 넘기지 않아(/api/ 만 연결) 서버의 Docker 네트워크 안에서만 부를 수 있고,
 * 그 위에 공유 토큰을 한 번 더 확인한다. 토큰이 설정되지 않았으면 아예 열지 않는다.
 */
@RestController
class VisitController {

	private final VisitService visits;

	private final String internalToken;

	VisitController(VisitService visits, @Value("${jipdon.internal-token:}") String internalToken) {
		this.visits = visits;
		this.internalToken = internalToken;
	}

	record PageView(String path, String ref) {
	}

	/** 화면이 페이지를 열 때 보내는 신호 (navigator.sendBeacon) */
	@PostMapping("/api/pv")
	ResponseEntity<Void> pageView(@RequestBody PageView body, HttpServletRequest request,
			@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
		visits.record(body.path(), body.ref(), request.getRemoteAddr(), userAgent);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/internal/stats")
	ResponseEntity<VisitStats> stats(@RequestHeader(value = "X-Internal-Token", required = false) String token,
			@RequestParam(defaultValue = "30") int days) {
		if (internalToken.isBlank() || token == null || !MessageDigest.isEqual(
				internalToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		return ResponseEntity.ok(visits.stats(Math.clamp(days, 1, 90)));
	}

}
