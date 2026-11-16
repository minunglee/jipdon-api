package io.github.minunglee.jipdon.collect;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import io.github.minunglee.jipdon.analysis.CacheVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DealCountStoreTest {

	static final Instant T0 = Instant.parse("2026-11-16T07:00:00Z");

	JdbcTemplate jdbc = mock(JdbcTemplate.class);

	CacheVersion cacheVersion = mock(CacheVersion.class);

	AtomicReference<Instant> now = new AtomicReference<>(T0);

	DealCountStore store;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		Clock clock = new Clock() {
			@Override
			public ZoneOffset getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(java.time.ZoneId zone) {
				return this;
			}

			@Override
			public Instant instant() {
				return now.get();
			}
		};
		store = new DealCountStore(jdbc, cacheVersion, clock);
		given(jdbc.queryForObject(anyString(), any(RowMapper.class)))
			.willAnswer(inv -> new DealCountStore.DealCounts(1, 2, 3, 4, now.get()));
	}

	@Test
	@SuppressWarnings("unchecked")
	void 캐시_버전이_같으면_다시_세지_않고_바뀌면_다시_센다() {
		given(cacheVersion.current()).willReturn("7");
		store.counts();
		store.counts();
		verify(jdbc, times(1)).queryForObject(anyString(), any(RowMapper.class));

		given(cacheVersion.current()).willReturn("8"); // 수집이 새 거래를 넣음
		assertThat(store.counts().total()).isEqualTo(2 + 3 + 4);
		verify(jdbc, times(2)).queryForObject(anyString(), any(RowMapper.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void Redis를_못_읽으면_10분까지는_지난_결과를_쓴다() {
		given(cacheVersion.current()).willReturn("7");
		store.counts();

		given(cacheVersion.current()).willReturn(null);
		now.set(T0.plus(DealCountStore.FALLBACK_TTL).minusSeconds(1));
		store.counts();
		verify(jdbc, times(1)).queryForObject(anyString(), any(RowMapper.class));

		now.set(T0.plus(DealCountStore.FALLBACK_TTL).plusSeconds(1));
		store.counts();
		verify(jdbc, times(2)).queryForObject(anyString(), any(RowMapper.class));
	}

}
