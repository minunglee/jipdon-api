-- 건물·면적별 최근 12개월 전세가율 요약. 수집 후 다시 계산해 순위 조회가 수백만 건의 거래를 매번 훑지 않게 한다
CREATE TABLE area_ratio (
    building_id   BIGINT        NOT NULL REFERENCES building (id),
    area          INTEGER       NOT NULL,  -- 전용면적 소수점 버림 ㎡
    sale_median   INTEGER       NOT NULL,  -- 만원
    sale_count    INTEGER       NOT NULL,
    jeonse_median INTEGER       NOT NULL,
    jeonse_count  INTEGER       NOT NULL,
    ratio         NUMERIC(5, 1) NOT NULL,  -- 전세 중앙값 ÷ 매매 중앙값 × 100
    PRIMARY KEY (building_id, area)
);

CREATE TABLE area_ratio_meta (
    id          INTEGER     PRIMARY KEY CHECK (id = 1),
    computed_at TIMESTAMPTZ NOT NULL,
    since       DATE        NOT NULL      -- 계산에 쓴 기간의 시작일
);
