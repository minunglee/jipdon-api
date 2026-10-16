package io.github.minunglee.jipdon.collect;

import java.util.List;

/** 실거래가 API 한 페이지 응답 */
public record RtmsPage(int totalCount, List<RtmsItem> items) {

}
