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

	/** 수집 대상 전체 지역 (앱이 뜰 때 regions.tsv에서 한 번 읽음) */
	public static final List<Region> ALL = load();

	/** 코드로 빨리 찾기 위한 색인 */
	private static final Map<String, Region> BY_CODE = ALL.stream()
		.collect(Collectors.toUnmodifiableMap(Region::code, Function.identity()));

	private Regions() {
	}

	/** 시군구 코드로 지역 찾기 (지원하지 않는 코드면 빈 값) */
	public static Optional<Region> find(String code) {
		return Optional.ofNullable(BY_CODE.get(code));
	}

	/** regions.tsv 읽기: 코드·시도·이름 탭 구분, # 줄과 빈 줄은 건너뜀 */
	private static List<Region> load() {
		try (InputStream in = Regions.class.getResourceAsStream("/regions.tsv");
				BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			List<Region> regions = new ArrayList<>();
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}
				String[] cols = line.strip().split("\t"); // Windows 체크아웃의 줄 끝(\r)도 떼어 낸다
				regions.add(new Region(cols[0], cols[1], cols[2]));
			}
			return List.copyOf(regions);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("지역 목록(regions.tsv)을 읽을 수 없습니다", ex);
		}
	}

}
