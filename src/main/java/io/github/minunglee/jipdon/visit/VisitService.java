package io.github.minunglee.jipdon.visit;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.github.minunglee.jipdon.JipdonProperties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * jipdon.com 방문 기록과 통계.
 *
 * 개인정보를 남기지 않도록 원래 IP는 저장하지 않는다. (IP + 브라우저 + 날짜)를 비밀값으로 HMAC 해 앞 16자만 남겨,
 * 같은 날 같은 사람인지는 셀 수 있지만 다음 날이면 연결되지 않고 원래 IP로 되돌릴 수도 없다. 쿠키도 쓰지 않는다.
 * 같은 사람이 같은 페이지를 30분 안에 다시 열면(새로고침 등) 조회를 한 번만 센다.
 */
@Service
public class VisitService {

	/** 저장할 경로 최대 길이 (비정상적으로 긴 주소가 DB를 채우지 않게) */
	private static final int MAX_PATH = 200;

	/** 이 시간 안에 같은 사람이 같은 페이지를 다시 열면 조회를 새로 세지 않는다 */
	static final Duration REPEAT_WINDOW = Duration.ofMinutes(30);

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final ZoneId zone;

	private final byte[] salt;

	VisitService(JdbcTemplate jdbc, Clock clock, JipdonProperties properties,
			@Value("${jipdon.visit.salt:local-dev-visit-salt}") String salt) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.zone = ZoneId.of(properties.zone());
		this.salt = salt.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * 방문 한 건을 기록한다. 로봇·스캐너(BotFilter)나 이상한 주소면 기록하지 않는다.
	 * @return 기록했으면 true
	 */
	public boolean record(String path, String referrer, String ip, String userAgent, String acceptLanguage) {
		// 비컨의 ref는 document.referrer라 직접 방문이면 비어 있는 게 정상 -> Referer 검사는 하지 않는다
		if (BotFilter.isBot(userAgent, acceptLanguage, null)) {
			return false;
		}
		String ua = userAgent;
		String cleanPath = cleanPath(path);
		if (cleanPath == null) {
			return false;
		}
		LocalDate today = LocalDate.now(clock.withZone(zone));
		String visitor = visitor(ip, ua, today);
		Boolean repeated = jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM page_view WHERE visitor = ? AND path = ? AND viewed_at > ?)", Boolean.class,
				visitor, cleanPath, Timestamp.from(clock.instant().minus(REPEAT_WINDOW)));
		if (Boolean.TRUE.equals(repeated)) {
			return false;
		}
		jdbc.update("INSERT INTO page_view (viewed_at, path, referrer_host, visitor, device) VALUES (?, ?, ?, ?, ?)",
				Timestamp.from(clock.instant()), cleanPath, referrerHost(referrer), visitor,
				ua.contains("Mobi") ? "mobile" : "desktop");
		return true;
	}

	/** 최근 days일 통계 (오늘 포함) */
	public VisitStats stats(int days) {
		LocalDate today = LocalDate.now(clock.withZone(zone));
		LocalDate from = today.minusDays(days - 1L);
		Timestamp since = Timestamp.from(from.atStartOfDay(zone).toInstant());
		String day = "(viewed_at AT TIME ZONE '" + zone.getId() + "')::date";

		List<VisitStats.Day> daily = jdbc.query("SELECT " + day + " AS d, count(*) AS views, count(DISTINCT visitor) AS visitors"
				+ " FROM page_view WHERE viewed_at >= ? GROUP BY 1 ORDER BY 1",
				(rs, i) -> new VisitStats.Day(rs.getDate("d").toLocalDate(), rs.getLong("views"), rs.getLong("visitors")),
				since);
		return new VisitStats(days, sum(daily, today, 1), sum(daily, today, 7), sum(daily, today, 30), daily,
				rank("path", since), rank("referrer_host", since), rank("device", since));
	}

	/** 오늘부터 거슬러 days일 동안의 조회·순방문자 합계 */
	private static VisitStats.Count sum(List<VisitStats.Day> daily, LocalDate today, int days) {
		LocalDate from = today.minusDays(days - 1L);
		long views = 0;
		long visitors = 0;
		for (VisitStats.Day d : daily) {
			if (!d.date().isBefore(from)) {
				views += d.views();
				visitors += d.visitors();
			}
		}
		return new VisitStats.Count(views, visitors);
	}

	/** 열(경로·유입 사이트·기기)별 많이 나온 순 상위 10개 */
	private List<VisitStats.Rank> rank(String column, Timestamp since) {
		return jdbc.query("SELECT " + column + " AS k, count(*) AS c FROM page_view WHERE viewed_at >= ? AND " + column
				+ " IS NOT NULL GROUP BY 1 ORDER BY 2 DESC, 1 LIMIT 10",
				(rs, i) -> new VisitStats.Rank(rs.getString("k"), rs.getLong("c")), since);
	}

	/** 주소의 경로만 남긴다 (검색어·# 제거). 이상한 값이면 null */
	static String cleanPath(String path) {
		if (path == null || !path.startsWith("/")) {
			return null;
		}
		String clean = path.split("[?#]", 2)[0];
		return clean.length() > MAX_PATH ? clean.substring(0, MAX_PATH) : clean;
	}

	/** 다른 사이트에서 왔을 때 그 사이트 주소(호스트)만. 같은 사이트 안 이동이면 null */
	static String referrerHost(String referrer) {
		if (referrer == null || referrer.isBlank()) {
			return null;
		}
		try {
			String host = URI.create(referrer.strip()).getHost();
			if (host == null) {
				return null;
			}
			host = host.toLowerCase(Locale.ROOT);
			if (host.equals("jipdon.com") || host.endsWith(".jipdon.com") || host.equals("localhost")) {
				return null;
			}
			return host.length() > 100 ? host.substring(0, 100) : host;
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	/** 순방문자 키: (IP + 브라우저 + 날짜)를 비밀값으로 HMAC 한 앞 16자. 같은 날 같은 사람만 같고, 원래 IP로 되돌릴 수 없다 */
	String visitor(String ip, String userAgent, LocalDate date) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(salt, "HmacSHA256"));
			byte[] hash = mac.doFinal((ip + "|" + userAgent + "|" + date).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash, 0, 8);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
