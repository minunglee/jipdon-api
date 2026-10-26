package io.github.minunglee.jipdon.collect;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import io.github.minunglee.jipdon.JipdonProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 국토교통부 실거래가 API 호출. 응답 해석은 {@link RtmsXmlParser}가 한다.
 *
 * 일시적인 네트워크 오류나 5xx는 잠깐 쉬었다가 최대 3번까지 다시 시도한다.
 * 인증키 오류·호출 한도 초과처럼 다시 해도 같은 결과인 오류는 바로 올려 보낸다.
 */
@Component
public class RtmsClient {

	private static final Logger log = LoggerFactory.getLogger(RtmsClient.class);

	/** API 요청 파라미터 계약년월 형식 (DEAL_YMD=202610) */
	private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

	/** 일시적 오류(네트워크·5xx) 때 최대 시도 횟수 */
	private static final int MAX_ATTEMPTS = 3;

	private final RestClient restClient;

	private final String serviceKey;

	private final String baseUrl;

	private final int pageSize;

	RtmsClient(JipdonProperties properties) {
		JipdonProperties.Rtms rtms = properties.rtms();
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(rtms.timeout());
		requestFactory.setReadTimeout(rtms.timeout());
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
		this.serviceKey = normalizeKey(rtms.serviceKey());
		this.baseUrl = rtms.baseUrl();
		this.pageSize = rtms.pageSize();
	}

	/** 한 번에 받는 거래 수 (페이지 크기) */
	public int pageSize() {
		return pageSize;
	}

	/** 인증키가 있는지 (없으면 수집을 건너뛴다) */
	public boolean configured() {
		return !serviceKey.isEmpty();
	}

	/**
	 * 포털에는 인코딩된 키(%2B 등)와 디코딩된 키가 함께 보인다. 어느 쪽을 넣어도 되게 원래 값으로 맞춘다.
	 * (인코딩된 키를 한 번 더 인코딩하면 "등록되지 않은 서비스키" 오류가 난다)
	 */
	static String normalizeKey(String key) {
		if (key == null || key.isBlank()) {
			return "";
		}
		String trimmed = key.strip();
		return trimmed.contains("%") ? URLDecoder.decode(trimmed, StandardCharsets.UTF_8) : trimmed;
	}

	public RtmsPage fetch(DealKind kind, String sggCd, YearMonth month, int pageNo) {
		if (!configured()) {
			throw new IllegalStateException("공공데이터포털 인증키(DATA_GO_KR_SERVICE_KEY)가 설정되지 않았습니다");
		}
		// 키의 +, /, = 를 반드시 인코딩해야 해서 URI를 직접 만든다
		URI uri = URI.create(baseUrl + kind.path() + "?serviceKey="
				+ URLEncoder.encode(serviceKey, StandardCharsets.UTF_8) + "&LAWD_CD=" + sggCd + "&DEAL_YMD="
				+ month.format(YYYYMM) + "&pageNo=" + pageNo + "&numOfRows=" + pageSize);

		for (int attempt = 1;; attempt++) {
			try {
				Response response = restClient.get()
					.uri(uri)
					.exchange((request, res) -> new Response(res.getStatusCode().value(),
							new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
				if (response.status() >= 500 && attempt < MAX_ATTEMPTS) {
					log.warn("실거래가 API {} 응답, 다시 시도 {}/{} ({} {} {})", response.status(), attempt, MAX_ATTEMPTS, kind,
							sggCd, month);
					pause(attempt);
					continue;
				}
				// 4xx(인증키 오류 등)도 본문의 오류 코드로 판단한다
				return RtmsXmlParser.parse(response.body(), kind);
			}
			catch (RestClientException ex) {
				if (attempt >= MAX_ATTEMPTS || !(ex.getCause() instanceof IOException)) {
					throw ex;
				}
				log.warn("실거래가 API 연결 오류, 다시 시도 {}/{} ({} {} {}): {}", attempt, MAX_ATTEMPTS, kind, sggCd, month,
						ex.getMessage());
				pause(attempt);
			}
		}
	}

	/** 재시도 전 대기: 1초, 2초 … (시도할수록 길게) */
	private static void pause(int attempt) {
		try {
			Thread.sleep(1000L * attempt);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	/** HTTP 상태와 본문 */
	private record Response(int status, String body) {
	}

}
