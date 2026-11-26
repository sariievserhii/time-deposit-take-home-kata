// k6 load test for the two Time Deposit endpoints. See java/README.md, section "Performance and load testing".
//
//   docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 grafana/k6:1.3.0 run - < load-test/time-deposits.js
//
// Start the app with the "loadtest" Liquibase context first, so there is realistic data (10,000 deposits).
// Credentials default to the local demo users (dev profile); override with -e VIEWER=user:pass -e OPERATOR=user:pass.
import http from 'k6/http';
import encoding from 'k6/encoding';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API = `${BASE_URL}/api/v1/time-deposits`;
const DURATION = __ENV.DURATION || '60s';
const basic = (credentials) => `Basic ${encoding.b64encode(credentials)}`;
const VIEWER_AUTH = basic(__ENV.VIEWER || 'viewer:viewer-demo-password');
const OPERATOR_AUTH = basic(__ENV.OPERATOR || 'operator:operator-demo-password');

export const options = {
    scenarios: {
        // Steady read traffic, at a fixed arrival rate so slow responses show up as latency rather than
        // silently lowering the request rate.
        reads: {
            executor: 'constant-arrival-rate',
            exec: 'listDeposits',
            rate: Number(__ENV.READ_RPS || 20),
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: 20,
            maxVUs: 100,
        },
        // A balance update every 10 s while reads are running. The first call of the month posts interest for
        // all 10,000 deposits (and writes 10,000 audit postings). Later calls in the same month are no-ops, but
        // still lock the deposits, so they exercise lock contention alongside the reads.
        writes: {
            executor: 'constant-arrival-rate',
            exec: 'updateBalances',
            rate: 1,
            timeUnit: '10s',
            duration: DURATION,
            preAllocatedVUs: 2,
        },
    },
    thresholds: {
        'http_req_failed': ['rate<0.01'],
        'http_req_duration{scenario:reads}': ['p(95)<500', 'p(99)<1000'],
        // Writes are a monthly batch, not an interactive call: the first POST of the month posts interest for
        // every deposit (about 5 s for 10,000 under full read load). The later no-op POSTs take ~150 ms. A
        // percentile over a handful of POSTs would just measure that one batch, so bound the batch time instead.
        'http_req_duration{scenario:writes}': ['max<10000', 'med<1000'],
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function listDeposits() {
    const res = http.get(API, { headers: { 'Accept-Encoding': 'gzip', Authorization: VIEWER_AUTH } });
    check(res, { 'GET 200': (r) => r.status === 200 });
}

export function updateBalances() {
    const res = http.post(`${API}/balance-updates`, null, { headers: { Authorization: OPERATOR_AUTH } });
    check(res, { 'POST 204': (r) => r.status === 204 });
}
