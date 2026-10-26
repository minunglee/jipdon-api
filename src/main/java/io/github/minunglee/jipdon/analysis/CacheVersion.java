package io.github.minunglee.jipdon.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 계산 결과 캐시의 버전. 캐시 키에 버전을 넣어 두고, 새 거래가 들어오면 버전만 올려 이전 캐시를 한 번에 버린다.
 * (키를 하나씩 찾아 지우지 않아도 되고, 옛 키는 만료 시간이 지나면 저절로 사라진다)
 */
@Component
public class CacheVersion {

	private static final Logger log = LoggerFactory.getLogger(CacheVersion.class);

	/** 현재 캐시 버전 숫자를 담는 Redis 키 */
	static final String KEY = "jipdon:cache:version";

	private final StringRedisTemplate redis;

	CacheVersion(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/** 현재 버전. Redis를 쓸 수 없으면 null (캐시 없이 계산한다) */
	public String current() {
		try {
			String version = redis.opsForValue().get(KEY);
			return version == null ? "0" : version;
		}
		catch (RuntimeException ex) {
			log.warn("Redis를 읽을 수 없어 캐시 없이 계산합니다: {}", ex.getMessage());
			return null;
		}
	}

	/** 버전을 1 올린다 → 이전 버전으로 만든 캐시 키는 더 이상 읽히지 않는다 (수집으로 새 거래가 들어왔을 때) */
	public void bump() {
		try {
			redis.opsForValue().increment(KEY);
		}
		catch (RuntimeException ex) {
			log.warn("캐시 버전을 올리지 못했습니다: {}", ex.getMessage());
		}
	}

}
