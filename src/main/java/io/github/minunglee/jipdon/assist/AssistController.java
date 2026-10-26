package io.github.minunglee.jipdon.assist;

import jakarta.servlet.http.HttpServletRequest;

import io.github.minunglee.jipdon.visit.BotFilter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 집돈 메인의 "무엇을 계산할까요?" 입력칸이 부르는 API */
@RestController
class AssistController {

	private final AssistService assistService;

	AssistController(AssistService assistService) {
		this.assistService = assistService;
	}

	/** @param question 사용자가 적은 질문 */
	record AssistRequest(String question) {
	}

	/** POST /api/assist: 질문 길이(1~200자)와 봇 여부를 확인한 뒤 AI 안내를 돌려준다 */
	@PostMapping("/api/assist")
	ResponseEntity<?> ask(@RequestBody AssistRequest request, HttpServletRequest servletRequest,
			@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
			@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
		String question = request.question() == null ? "" : request.question().strip();
		if (question.isEmpty() || question.length() > AssistService.MAX_QUESTION) {
			return problem(HttpStatus.BAD_REQUEST,
					"질문은 1~" + AssistService.MAX_QUESTION + "자로 입력해 주세요.");
		}
		// 로봇이 AI 예산을 쓰지 않게, 방문 통계와 같은 기준으로 사람의 브라우저가 아니면 막는다
		if (BotFilter.isBot(userAgent, acceptLanguage, null)) {
			return problem(HttpStatus.FORBIDDEN, "브라우저에서 이용해 주세요.");
		}
		return ResponseEntity.ok(assistService.ask(question, servletRequest.getRemoteAddr()));
	}

	/** 오늘 한도 초과면 429, 게이트웨이에 닿지 않으면 503 */
	@ExceptionHandler(AssistService.AssistUnavailableException.class)
	ResponseEntity<ProblemDetail> unavailable(AssistService.AssistUnavailableException ex) {
		return problem(ex.rateLimited ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.SERVICE_UNAVAILABLE,
				ex.getMessage());
	}

	/** 화면에 그대로 보여줄 오류 응답 */
	private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, message));
	}

}
