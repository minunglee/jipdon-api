package io.github.minunglee.jipdon.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 잘못된 요청은 화면에 그대로 보여줄 수 있는 한국어 메시지로 답한다 */
@RestControllerAdvice
class ApiErrors {

	/** 잘못된 요청 → 400 + 화면에 보여줄 메시지 */
	@ExceptionHandler(BadRequestException.class)
	ResponseEntity<Map<String, String>> badRequest(BadRequestException ex) {
		return message(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	/** 파라미터 누락·형식 오류 → 400 */
	@ExceptionHandler({ MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class })
	ResponseEntity<Map<String, String>> invalidParameter(Exception ex) {
		return message(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.");
	}

	/** 없는 건물 등 → 404 */
	@ExceptionHandler(NotFoundException.class)
	ResponseEntity<Map<String, String>> notFound(NotFoundException ex) {
		return message(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	/** {"message": "..."} 형태 응답 */
	private static ResponseEntity<Map<String, String>> message(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of("message", message));
	}

}

class BadRequestException extends RuntimeException {

	BadRequestException(String message) {
		super(message);
	}

}

class NotFoundException extends RuntimeException {

	NotFoundException(String message) {
		super(message);
	}

}
