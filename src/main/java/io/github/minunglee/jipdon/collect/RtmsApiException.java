package io.github.minunglee.jipdon.collect;

/**
 * 공공 API가 오류 코드를 돌려준 경우.
 *
 * @param quotaExceeded 하루 호출 한도 초과. 이번 수집을 멈추고 다음 실행(한도 초기화 후)에 이어서 한다
 */
public class RtmsApiException extends RuntimeException {

	private final String code;

	private final boolean quotaExceeded;

	public RtmsApiException(String code, String message, boolean quotaExceeded) {
		super("실거래가 API 오류 [" + code + "] " + message);
		this.code = code;
		this.quotaExceeded = quotaExceeded;
	}

	/** 공공데이터포털 오류 코드 */
	public String code() {
		return code;
	}

	/** 하루 호출 한도 초과인지 (이 경우 수집을 멈추고 다음 실행에서 이어 간다) */
	public boolean quotaExceeded() {
		return quotaExceeded;
	}

}
