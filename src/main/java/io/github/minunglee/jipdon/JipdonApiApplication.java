package io.github.minunglee.jipdon;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class JipdonApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(JipdonApiApplication.class, args);
	}

	/** 날짜 계산의 기준 시계. 테스트에서 고정된 날짜로 바꿔 끼운다 */
	@Bean
	Clock clock(JipdonProperties properties) {
		return Clock.system(ZoneId.of(properties.zone()));
	}

}
