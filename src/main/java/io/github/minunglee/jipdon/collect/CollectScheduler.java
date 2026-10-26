package io.github.minunglee.jipdon.collect;

import io.github.minunglee.jipdon.analysis.RankingService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 수집 일정. 운영 서버에서만 켠다 (jipdon.collect.enabled=true).
 * 앱이 뜬 직후 한 번, 이후 6시간마다 실행한다. 할 일이 없으면 API를 부르지 않고 바로 끝난다.
 */
@Component
@ConditionalOnBooleanProperty("jipdon.collect.enabled")
class CollectScheduler {

	private final Collector collector;

	private final RankingService rankingService;

	CollectScheduler(Collector collector, RankingService rankingService) {
		this.collector = collector;
		this.rankingService = rankingService;
	}

	/** 앱이 뜬 직후: 순위 요약이 비어 있으면 먼저 만들고 수집을 한 번 돌린다 (별도 스레드라 시작을 막지 않음) */
	@EventListener(ApplicationReadyEvent.class)
	void onStartup() {
		// 시작을 막지 않도록 별도 스레드에서 돈다
		Thread.ofVirtual().name("collect-startup").start(() -> {
			rankingService.refreshIfEmpty(); // 처음 배포 직후에도 순위를 볼 수 있게
			collector.run();
		});
	}

	/** 정해진 시각(6시간마다, 한국 시간)에 수집 */
	@Scheduled(cron = "${jipdon.collect.cron}", zone = "${jipdon.zone}")
	void scheduled() {
		collector.run();
	}

}
