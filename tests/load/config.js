import { SharedArray } from 'k6/data';

export const base = (__ENV.BASE_URL || 'http://localhost').replace(/\/$/, '');
export const mode = __ENV.MODE || 'smoke';
if (!['smoke', 'mixed', 'search'].includes(mode)) throw new Error('MODE must be smoke, mixed or search');
export const peak = Number(__ENV.VUS || (mode === 'smoke' ? 2 : 200));
if (!Number.isInteger(peak) || peak < 1) throw new Error('VUS must be a positive integer');
export const data = new SharedArray('fixtures', () => [JSON.parse(open(__ENV.FIXTURES || './fixtures.example.json'))])[0];
if (!data.productIds.length || !data.keywords.length) throw new Error('Product IDs and keywords required');
if (mode !== 'search' && data.users.length < peak) throw new Error('One distinct user/token per VU required');
export const options = {
  scenarios: { workload: mode === 'smoke' ? { executor: 'constant-vus', vus: peak, duration: __ENV.HOLD || '30s' } : {
    executor: 'ramping-vus', startVUs: 0, stages: [
      { duration: __ENV.RAMP || '5m', target: peak },
      { duration: __ENV.HOLD || '15m', target: peak },
      { duration: __ENV.DOWN || '1m', target: 0 },
    ], gracefulRampDown: '30s',
  } },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{kind:read}': ['med<1000', 'p(95)<2000'],
    business_errors: ['rate<0.01'],
  },
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
};
