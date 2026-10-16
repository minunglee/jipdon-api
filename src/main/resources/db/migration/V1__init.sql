-- 건물(아파트 단지·빌라). 실거래가 API에는 고유 번호가 없어 지역·동·지번·이름으로 구분한다
CREATE TABLE building (
    id         BIGSERIAL PRIMARY KEY,
    kind       VARCHAR(3) NOT NULL,          -- APT: 아파트, RH: 연립·다세대
    sgg_cd     CHAR(5)    NOT NULL,          -- 시군구 코드 (법정동코드 앞 5자리)
    umd_nm     TEXT       NOT NULL,          -- 법정동
    jibun      TEXT       NOT NULL,
    name       TEXT       NOT NULL,
    house_type TEXT,                         -- 아파트·연립·다세대
    build_year INTEGER,
    CONSTRAINT building_uk UNIQUE (kind, sgg_cd, umd_nm, jibun, name)
);

-- 매매. 금액 단위는 만원 (API와 같음)
CREATE TABLE sale_deal (
    id             BIGSERIAL PRIMARY KEY,
    building_id    BIGINT        NOT NULL REFERENCES building (id),
    exclusive_area NUMERIC(8, 2) NOT NULL,   -- 전용면적 ㎡
    floor          INTEGER,
    deal_date      DATE          NOT NULL,
    price_man      INTEGER       NOT NULL,
    canceled       BOOLEAN       NOT NULL DEFAULT FALSE, -- 계약 해제 신고
    natural_key    CHAR(32)      NOT NULL,   -- 같은 거래를 다시 받아도 한 줄만 남기기 위한 값 (거래 내용의 해시)
    CONSTRAINT sale_deal_natural_key_uk UNIQUE (natural_key)
);
CREATE INDEX sale_deal_building_date_idx ON sale_deal (building_id, deal_date);

-- 전월세. 월세가 0이면 전세
CREATE TABLE rent_deal (
    id                BIGSERIAL PRIMARY KEY,
    building_id       BIGINT        NOT NULL REFERENCES building (id),
    exclusive_area    NUMERIC(8, 2) NOT NULL,
    floor             INTEGER,
    deal_date         DATE          NOT NULL,
    deposit_man       INTEGER       NOT NULL,
    monthly_rent_man  INTEGER       NOT NULL,
    contract_type     TEXT,                  -- 신규·갱신
    natural_key       CHAR(32)      NOT NULL,
    CONSTRAINT rent_deal_natural_key_uk UNIQUE (natural_key)
);
CREATE INDEX rent_deal_building_date_idx ON rent_deal (building_id, deal_date);

-- 지역·종류·월 단위 수집 기록. 이어서 수집하고, 화면에 마지막 갱신 시각을 보여주는 데 쓴다
CREATE TABLE collect_log (
    sgg_cd       CHAR(5)     NOT NULL,
    deal_kind    VARCHAR(8)  NOT NULL,
    deal_ym      CHAR(6)     NOT NULL,
    row_count    INTEGER     NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (sgg_cd, deal_kind, deal_ym)
);
