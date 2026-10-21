-- 단독·다가구 거래. 지번이 가려져 오고 건물 이름이 없어 건물별로 묶지 않고 동 단위 통계에만 쓴다
CREATE TABLE detached_deal (
    id               BIGSERIAL PRIMARY KEY,
    sgg_cd           CHAR(5)        NOT NULL,
    umd_nm           TEXT           NOT NULL,
    house_type       TEXT,                     -- 단독·다가구
    deal_type        VARCHAR(4)     NOT NULL,  -- SALE: 매매, RENT: 전월세
    total_floor_area NUMERIC(10, 2) NOT NULL,  -- 매매: 건물 연면적, 전월세: 계약 면적 (㎡)
    plottage_area    NUMERIC(10, 2),           -- 매매: 대지면적
    build_year       INTEGER,
    deal_date        DATE           NOT NULL,
    price_man        INTEGER,                  -- 매매가 (만원)
    deposit_man      INTEGER,                  -- 보증금 (만원)
    monthly_rent_man INTEGER,                  -- 월세 (만원), 0이면 전세
    canceled         BOOLEAN        NOT NULL DEFAULT FALSE,
    natural_key      CHAR(32)       NOT NULL,
    CONSTRAINT detached_deal_natural_key_uk UNIQUE (natural_key)
);
CREATE INDEX detached_deal_region_date_idx ON detached_deal (sgg_cd, deal_date);

-- 오피스텔(OFFI)과 OFFI_SALE·OFFI_RENT가 들어가도록 종류 칸을 넓힌다
ALTER TABLE building ALTER COLUMN kind TYPE VARCHAR(8);
ALTER TABLE collect_log ALTER COLUMN deal_kind TYPE VARCHAR(16);
