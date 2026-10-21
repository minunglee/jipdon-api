// jipdon-api 조회 부하 테스트 (k6)
// 운영 서버에서 Docker 네트워크 안의 앱을 직접 부른다 (Nginx의 IP당 초당 5회 제한을 거치지 않음).
//   docker run --rm --network app_default -v $PWD:/lt grafana/k6:0.54.0 run -e SCENARIO=analysis_miss /lt/api.js
// SCENARIO: analysis_miss | analysis_hit | ranking | search
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://jipdon-api:8090';
// 최근 12개월 매매·전세가 모두 있는 건물 id (area_ratio에서 뽑은 목록)
const ids = JSON.parse(open('./building-ids.json'));
const regions = ['11440', '11680', '11710', '41135', '26350', '27260', '12300', '30200', '48121', '50110'];
const sidos = ['', '서울', '경기', '부산', '대구', '광주', '전남', '경남'];
const kinds = ['APT', 'RH', 'OFFI'];
const pick = (list) => list[Math.floor(Math.random() * list.length)];

export const options = {
	vus: Number(__ENV.VUS || 20),
	duration: __ENV.DURATION || '20s',
	thresholds: { http_req_failed: ['rate<0.01'] },
	summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
	let res;
	switch (__ENV.SCENARIO) {
		case 'analysis_miss': // 서로 다른 건물 → 대부분 캐시에 없어 DB에서 계산
			res = http.get(`${BASE}/api/buildings/${pick(ids)}/jeonse`, { tags: { name: 'analysis' } });
			break;
		case 'analysis_hit': // 같은 20개 건물을 반복 → 캐시에서 읽음
			res = http.get(`${BASE}/api/buildings/${ids[Math.floor(Math.random() * 20)]}/jeonse`, { tags: { name: 'analysis' } });
			break;
		case 'ranking':
			res = http.get(`${BASE}/api/rankings?kind=${pick(kinds)}${pick(sidos) ? `&sido=${encodeURIComponent(pick(sidos))}` : ''}`, { tags: { name: 'ranking' } });
			break;
		case 'search':
			res = http.get(`${BASE}/api/buildings?region=${pick(regions)}&kind=${pick(kinds)}&q=`, { tags: { name: 'search' } });
			break;
		default:
			throw new Error('SCENARIO를 지정하세요');
	}
	check(res, { 'status 200': (r) => r.status === 200 });
}
