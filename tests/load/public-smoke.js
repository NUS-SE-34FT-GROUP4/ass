import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
const base = (__ENV.BASE_URL || 'http://localhost').replace(/\/$/, '');
const empty = new Rate('empty_results');
export const options = {
  vus: 2, duration: '30s',
  thresholds: { http_req_failed: ['rate<0.01'], checks: ['rate==1'], http_req_duration: ['med<1000', 'p(95)<2000'] },
  summaryTrendStats: ['med', 'p(95)', 'max'],
};
export default function () {
  for (const [name, path] of [
    ['browse', '/api/products'],
    ['search', `${__ENV.SEARCH_PATH || '/api/products/search'}?keyword=${encodeURIComponent(__ENV.KEYWORD || 'book')}&page=0&size=20`],
  ]) {
    const r = http.get(base + path, { tags: { name }, timeout: '10s' });
    let body;
    try { body = r.json(); } catch (_) { body = null; }
    check(r, { '200 JSON array': () => r.status === 200 && Array.isArray(body) });
    if (Array.isArray(body)) empty.add(body.length === 0, { name });
  }
  sleep(1);
}
export function handleSummary(summary) {
  return { [__ENV.SUMMARY || 'public-smoke-summary.json']: JSON.stringify({
    scope: 'Anonymous browse/search only; empty arrays do not prove seeded-data readiness',
    base, timestamp: new Date().toISOString(), ...summary,
  }, null, 2) };
}
