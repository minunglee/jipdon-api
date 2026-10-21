-- jipdon.com 방문 기록. 원래 IP는 저장하지 않고, (IP + 브라우저 + 날짜)의 해시로 하루 단위 순방문자만 센다
CREATE TABLE page_view (
    id            BIGSERIAL PRIMARY KEY,
    viewed_at     TIMESTAMPTZ  NOT NULL,
    path          VARCHAR(200) NOT NULL,
    referrer_host VARCHAR(100),          -- 다른 사이트에서 왔을 때만 (예: www.google.com)
    visitor       CHAR(16)     NOT NULL,  -- 하루 단위 순방문자 해시 (다음 날이면 같은 사람도 다른 값)
    device        VARCHAR(8)   NOT NULL   -- mobile · desktop
);
CREATE INDEX page_view_viewed_at_idx ON page_view (viewed_at);
