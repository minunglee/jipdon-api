package io.github.minunglee.jipdon;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jipdon")
public record JipdonProperties(String zone, Rtms rtms, Collect collect, Analysis analysis) {

	/** 국토교통부 실거래가 공공 API (공공데이터포털) */
	public record Rtms(String serviceKey, String baseUrl, int pageSize, Duration timeout) {
	}

	/** 수집 배치 */
	public record Collect(boolean enabled, String cron, int historyMonths, int refreshMonths, Duration refreshInterval,
			int maxCallsPerRun) {
	}

	/** 전세가율 계산 */
	public record Analysis(int recentMonths, int fallbackMonths, Duration cacheTtl) {
	}

}
