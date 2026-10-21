# 부하 테스트

운영 서버(가상서버 2GB · vCPU 4, 게이트웨이와 공유)에서 실제 데이터로 조회 API를 측정했습니다.

- 측정일: 2026-10-21
- 데이터: 건물 22만 4천 개, 실거래 366만 건, 건물·면적 전세가율 요약 6만 개 (DB 924MB)
- 도구: k6 0.54.0. 서버 안 Docker 네트워크에서 앱(`jipdon-api:8090`)을 직접 부릅니다. Nginx의 IP당 초당 5회 제한은 거치지 않습니다.
- 부하: 가상 사용자 20명, 시나리오마다 20초. 운영 중인 서비스라 짧게 측정했습니다.

## 결과

| 시나리오 | 내용 | 처리량 | 중앙값 | p95 | p99 | 실패 |
| --- | --- | --- | --- | --- | --- | --- |
| `analysis_miss` | 건물 3,000개 중 무작위 분석 (대부분 캐시 없음 → DB 계산) | 678건/초 | 9.7ms | 127ms | 260ms | 0% |
| `analysis_hit` | 같은 건물 20개 반복 (Redis 캐시) | 4,266건/초 | 3.3ms | **11ms** | 25ms | 0% |
| `ranking` | 동네 순위, 종류·시도 조합 (요약 표 + 캐시) | 4,032건/초 | 3.6ms | 9.9ms | 16ms | 0% |
| `search` | 지역 안 건물 검색 (거래 많은 순 20개) | 441건/초 | 38ms | 92ms | 151ms | 0% |

측정 중 메모리: 앱 329MB / 한도 448MB, PostgreSQL 204MB / 한도 256MB.

## 해석

- **캐시 효과**: 같은 건물 분석은 캐시에서 p95 11ms로, DB에서 계산할 때(127ms)보다 약 11배 빠릅니다.
  분석 결과는 새 거래가 들어오기 전까지 바뀌지 않으므로, 캐시 키에 버전을 두고 수집 후에만 무효화합니다.
- **동네 순위**: 366만 건을 요청마다 훑으면 수 초가 걸립니다. 수집 후 건물·면적별 요약 표(6만 개)를 미리 만들어 두어
  순위 조회는 요약 표와 캐시만 읽습니다 (요약 계산 자체는 운영 데이터에서 약 3.6초, 수집 후 한 번).
- **검색**: 후보 200개를 먼저 줄이고, 그 안에서만 거래 수를 셉니다. 검색어가 짧아도 수만 개 건물의 거래 수를 다 세지 않습니다.
- 실제 사용자는 Nginx 제한(IP당 초당 5회, 순간 20회)을 거치므로, 이 수치는 앱·DB가 감당할 수 있는 여유를 보여 줍니다.

## 다시 측정하기

```bash
# 서버에서 (scp loadtest/api.js 후)
docker exec app-postgres-1 psql -U gateway -d jipdon -tAc \
  "SELECT json_agg(building_id) FROM (SELECT building_id FROM (SELECT DISTINCT building_id FROM area_ratio) d ORDER BY random() LIMIT 3000) t" \
  > lt/building-ids.json
for s in analysis_miss analysis_hit ranking search; do
  docker run --rm --network app_default -v $PWD/lt:/lt -w /lt grafana/k6:0.54.0 run -q -e SCENARIO=$s /lt/api.js
done
```
