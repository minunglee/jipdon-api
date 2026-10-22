-- 수집 배치 한 번 실행할 때마다 결과를 남긴다 (관리자 화면의 수집 현황용)
CREATE TABLE collect_run (
    id               BIGSERIAL   PRIMARY KEY,
    started_at       TIMESTAMPTZ NOT NULL,
    finished_at      TIMESTAMPTZ NOT NULL,
    tasks            INTEGER     NOT NULL,
    calls            INTEGER     NOT NULL,
    changed_rows     INTEGER     NOT NULL,
    failed_tasks     INTEGER     NOT NULL,
    stopped_by_quota BOOLEAN     NOT NULL,
    remaining_tasks  INTEGER     NOT NULL
);

CREATE INDEX collect_run_started_at ON collect_run (started_at DESC);
