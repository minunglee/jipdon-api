package io.github.minunglee.jipdon.assist;

import io.github.minunglee.jipdon.assist.AssistService.Answer;
import io.github.minunglee.jipdon.assist.GatewayClient.Result;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AssistServiceTest {

	GatewayClient gateway = mock(GatewayClient.class);

	AssistService service = new AssistService(gateway, JsonMapper.builder().build(), "test-salt");

	Answer answer(String modelOutput) {
		given(gateway.complete(any())).willReturn(new Result.Answered(modelOutput, false));
		return service.ask("질문", "203.0.113.7");
	}

	@Test
	void 계산기와_입력값을_링크로_만든다() {
		Answer answer = answer("""
				{"relevant":true,"tool":"jeonse-loan","fields":{"deposit":400000000,"ratio":80},
				 "answer":"전세대출 이자 계산기에서 매달 이자를 확인해 보세요."}""");

		assertThat(answer.relevant()).isTrue();
		assertThat(answer.link().href()).isEqualTo("/jeonse-loan/?f.deposit=400000000&f.ratio=80");
		assertThat(answer.link().name()).isEqualTo("전세대출 이자 계산기");
		assertThat(answer.link().filled()).extracting(AssistService.Filled::value)
			.containsExactly("400,000,000원", "80%");
	}

	@Test
	void AI가_지어낸_계산기_입력칸_범위밖_값은_버린다() {
		assertThat(answer("{\"relevant\":true,\"tool\":\"https://evil.example\",\"answer\":\"여기로 가세요\"}").link())
			.isNull();

		Answer answer = answer("""
				{"relevant":true,"tool":"car-installment",
				 "fields":{"price":35000000,"months":"18","rate":250,"script":"<b>x</b>"},"answer":"할부 계산기"}""");
		assertThat(answer.link().href()).isEqualTo("/car-installment/?f.price=35000000");
	}

	@Test
	void 선택값과_단위는_사람이_읽는_말로_보여준다() {
		Answer answer = answer("""
				{"relevant":true,"tool":"rent-conversion","fields":{"mode":"to-rent","deposit":100000000},"answer":"전환"}""");
		assertThat(answer.link().filled()).extracting(f -> f.label() + ": " + f.value())
			.containsExactly("방향: 보증금→월세", "바꿀 보증금: 100,000,000원");

		Answer car = answer("""
				{"relevant":true,"tool":"loan","fields":{"term":30},"answer":"대출"}""");
		assertThat(car.link().filled()).extracting(f -> f.label() + ": " + f.value()).containsExactly("기간: 30년");
	}

	@Test
	void 관련_없는_질문은_정해진_문구만_보낸다() {
		Answer answer = answer("{\"relevant\":false}");
		assertThat(answer.relevant()).isFalse();
		assertThat(answer.text()).isEqualTo(AssistService.OFF_TOPIC);
		assertThat(answer.link()).isNull();
	}

	@Test
	void 형식이_깨진_답은_다시_물어보게_한다() {
		assertThat(answer("죄송하지만 잘 모르겠어요").text()).isEqualTo(AssistService.NOT_UNDERSTOOD);
		// 앞뒤에 설명이 붙어 와도 JSON 부분만 읽는다
		assertThat(answer("네! {\"relevant\":true,\"tool\":\"dsr\",\"answer\":\"DSR 계산기\"} 입니다").link().href())
			.isEqualTo("/dsr/");
	}

	@Test
	void 게이트웨이가_막으면_이유를_알린다() {
		given(gateway.complete(any())).willReturn(new Result.RateLimited());
		assertThatThrownBy(() -> service.ask("질문", "203.0.113.7"))
			.isInstanceOfSatisfying(AssistService.AssistUnavailableException.class, ex -> assertThat(ex.rateLimited).isTrue());

		given(gateway.complete(any())).willReturn(new Result.Answered("[데모 응답] 오늘 예산 소진", true));
		assertThat(service.ask("질문", "203.0.113.7").text()).startsWith("[데모 응답]");
	}

	@Test
	void 게이트웨이에는_원래_IP_대신_해시를_넘긴다() {
		String subject = service.subject("203.0.113.7");
		assertThat(subject).hasSize(32).doesNotContain("203");
		assertThat(service.subject("203.0.113.7")).isEqualTo(subject);
		assertThat(service.subject("198.51.100.9")).isNotEqualTo(subject);
	}

	@Test
	void 시스템_프롬프트에_모든_계산기가_들어간다() {
		for (AssistCatalog.Tool tool : AssistCatalog.TOOLS) {
			assertThat(AssistService.SYSTEM_PROMPT).contains("- " + tool.id() + ":");
		}
	}

}
