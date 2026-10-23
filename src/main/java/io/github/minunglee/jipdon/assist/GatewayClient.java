package io.github.minunglee.jipdon.assist;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 같은 서버의 AI 게이트웨이(minunglee.cloud)에 AI 호출을 맡긴다.
 * 사용량 제한·예산·긴급 차단은 게이트웨이가 지키고, 이 서버는 질문을 만들고 결과를 검사한다.
 * 주소(app:8080/internal/llm)는 Docker 네트워크 안에서만 닿고, 방문 통계와 같은 공유 토큰을 보낸다.
 */
@Component
class GatewayClient {

	private static final Logger log = LoggerFactory.getLogger(GatewayClient.class);

	sealed interface Result {

		/** @param degraded 게이트웨이가 예산 소진·긴급 차단으로 AI를 부르지 않았으면 true (answer는 안내 문구) */
		record Answered(String answer, boolean degraded) implements Result {
		}

		/** 이 사용자의 오늘 한도를 다 씀 */
		record RateLimited() implements Result {
		}

		/** 게이트웨이에 닿지 않거나 토큰이 없음 */
		record Unavailable() implements Result {
		}

	}

	record Request(String subject, String system, String message, int maxTokens) {
	}

	record Response(String answer, boolean degraded) {
	}

	private final RestClient restClient;

	private final String url;

	private final String token;

	GatewayClient(@Value("${jipdon.assist.gateway-url:http://app:8080/internal/llm}") String url,
			@Value("${jipdon.internal-token:}") String token) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(2));
		// Nginx가 /api/ 응답을 15초까지 기다리므로 그 안에 끝낸다
		requestFactory.setReadTimeout(Duration.ofSeconds(12));
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
		this.url = url;
		this.token = token;
	}

	Result complete(Request request) {
		if (token.isBlank()) {
			return new Result.Unavailable();
		}
		try {
			Response response = restClient.post()
				.uri(url)
				.header("X-Internal-Token", token)
				.body(request)
				.retrieve()
				.body(Response.class);
			return response == null ? new Result.Unavailable()
					: new Result.Answered(response.answer(), response.degraded());
		}
		catch (HttpClientErrorException.TooManyRequests ex) {
			return new Result.RateLimited();
		}
		catch (RestClientException ex) {
			log.warn("AI 게이트웨이 호출 실패: {}", ex.getMessage());
			return new Result.Unavailable();
		}
	}

}
