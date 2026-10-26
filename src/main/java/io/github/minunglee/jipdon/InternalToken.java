package io.github.minunglee.jipdon;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * /internal/** 주소용 공유 토큰. 같은 서버의 게이트웨이(관리자 화면)만 알고 있다.
 * 토큰이 설정되지 않았으면 어떤 요청도 통과시키지 않는다.
 */
@Component
public class InternalToken {

	private final byte[] token;

	InternalToken(@Value("${jipdon.internal-token:}") String token) {
		this.token = token.getBytes(StandardCharsets.UTF_8);
	}

	/** 토큰이 설정돼 있고 요청 토큰과 같은지 (상수 시간 비교) */
	public boolean matches(String candidate) {
		return token.length > 0 && candidate != null
				&& MessageDigest.isEqual(token, candidate.getBytes(StandardCharsets.UTF_8));
	}

}
