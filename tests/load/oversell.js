import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { base, data } from './config.js';
const buyers = Number(__ENV.BUYERS || 20);
const productId = Number(__ENV.PRODUCT_ID);
const stock = Number(__ENV.INITIAL_STOCK || 1);
const paid = new Counter('paid_orders');
const auditFailed = new Rate('inventory_audit_failed');
export const options = {
  scenarios: { purchase: { executor: 'per-vu-iterations', vus: buyers, iterations: 1, maxDuration: '1m' } },
  thresholds: { paid_orders: [`count==${stock}`], inventory_audit_failed: ['rate==0'] },
};
const params = user => ({ headers: { Authorization: `Bearer ${user.token}`, 'Content-Type': 'application/json' }, tags: { name: 'oversell_pay' }, timeout: '10s' });
export function setup() {
  if (__ENV.ENABLE_WRITES !== 'true' || !productId || !Number.isInteger(stock) || stock < 1 || stock > buyers || data.users.length < buyers) throw Error('Dedicated product, funded buyers, initial stock and ENABLE_WRITES=true required');
  if (!Number.isInteger(buyers) || buyers < 1 || data.users.slice(0, buyers).some(u => !u.token || u.token.startsWith('REPLACE') || !/^\d{6}$/.test(u.paymentPassword || ''))) throw Error('Valid buyer tokens and six-digit payment passwords required');
  const before = http.get(`${base}/api/products/${productId}`);
  if (before.status !== 200 || before.json('stock') !== stock) throw Error('Initial stock does not match');
  // Create ALL orders before concurrent payment, avoiding creation-time stock rejection.
  const orders = data.users.slice(0, buyers).map(user => {
    const r = http.post(`${base}/api/orders/buy-now`, JSON.stringify({ productId, quantity: 1 }), params(user));
    if (r.status !== 200 || !r.json('id')) throw Error('Order preparation failed');
    return r.json('id');
  });
  return orders;
}
export default function (orders) {
  const user = data.users[__VU - 1];
  const r = http.post(`${base}/api/orders/${orders[__VU-1]}/pay`, JSON.stringify({ paymentMethod: 'BALANCE', password: user.paymentPassword }), params(user));
  const success = r.status === 200 && r.json('status') === 'PAID';
  paid.add(success ? 1 : 0);
  // Rejection statuses are recorded, never reclassified as successful payments.
  check(r, { 'payment paid or rejected': () => success || r.status >= 400 });
}
export function teardown(orders) {
  let paidCount = 0;
  let valid = true;
  for (let i = 0; i < orders.length; i++) {
    const r = http.get(`${base}/api/orders/${orders[i]}`, params(data.users[i]));
    if (r.status !== 200) { valid = false; continue; }
    if (r.json('status') === 'PAID') paidCount++;
  }
  const r = http.get(`${base}/api/products/${productId}`);
  const finalStock = r.status === 200 ? r.json('stock') : null;
  auditFailed.add(!valid || paidCount !== stock || finalStock !== 0);
}
