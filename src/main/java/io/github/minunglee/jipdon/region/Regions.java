package io.github.minunglee.jipdon.region;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 수집 대상 지역: 전국 256개 시군구. 목록은 regions.tsv 에 있고, 모든 코드를 실제 API로 확인했다.
 * 2026년 행정구역 개편으로 바뀐 코드(인천·화성·광주전남 통합)는 regions.tsv 머리말 참고.
 */
public final class Regions {

	public static final List<Region> ALL = load();

	private static final Map<String, Region> BY_CODE = ALL.stream()
		.collect(Collectors.toUnmodifiableMap(Region::code, Function.identity()));

	private Regions() {
	}

	public static Optional<Region> find(String code) {
		return Optional.ofNullable(BY_CODE.get(code));
	}

	private static List<Region> load() {
		try (InputStream in = Regions.class.getResourceAsStream("/regions.tsv");
				BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			List<Region> regions = new ArrayList<>();
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}
				String[] cols = line.split("\t");
				regions.add(new Region(cols[0], cols[1], cols[2]));
			}
			return List.copyOf(regions);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("지역 목록(regions.tsv)을 읽을 수 없습니다", ex);
		}
	}

}
