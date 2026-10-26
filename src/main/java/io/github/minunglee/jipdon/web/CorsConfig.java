package io.github.minunglee.jipdon.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 운영에서는 화면과 API가 같은 도메인(jipdon.com)이라 필요 없다.
 * 로컬 개발에서 Astro 개발 서버(localhost:4321)가 이 앱을 직접 부를 수 있게, 읽기(GET)만 허용한다.
 */
@Configuration(proxyBeanMethods = false)
class CorsConfig implements WebMvcConfigurer {

	private final String[] allowedOrigins;

	CorsConfig(@Value("${jipdon.cors.allowed-origins:http://localhost:4321}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	/** 로컬 개발 화면(4321)이 이 앱을 직접 부를 수 있게 허용 (운영은 같은 도메인이라 해당 없음) */
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		// AI 계산기 안내만 질문을 보낸다(POST)
		registry.addMapping("/api/assist").allowedOrigins(allowedOrigins).allowedMethods("POST");
		registry.addMapping("/api/**").allowedOrigins(allowedOrigins).allowedMethods("GET");
	}

}
