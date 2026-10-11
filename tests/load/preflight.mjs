import fs from 'node:fs';
import assert from 'node:assert/strict';
const base = 'http://backend:8080';
const dir = 'tests/load/generated';
const credentials = JSON.parse(fs.readFileSync(`${dir}/credentials.json`));
const fixtures = JSON.parse(fs.readFileSync(`${dir}/fixtures.json`));
const manifest = JSON.parse(fs.readFileSync(`${dir}/manifest.json`));
async function request(path, options = {}) {
  const r = await fetch(base + path, { ...options, signal: AbortSignal.timeout(120000) });
  assert.ok(r.ok, `${path}: HTTP ${r.status}`);
  const text = await r.text();
  try { return JSON.parse(text); } catch { return text; }
}
// The local runner provisions a one-use captcha in its isolated Redis only.
// Login still executes the application's AuthenticationManager and BCrypt.
const login = await request('/api/auth/login', { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify({username:credentials.username,password:credentials.password,captchaId:process.env.CAPTCHA_ID,captchaCode:process.env.CAPTCHA_CODE}) });
assert.ok(login.token, 'Real password login did not return JWT');
assert.equal(login.username, credentials.username);
const headers = {Authorization: `Bearer ${fixtures.users[0].token}`};
await request('/api/cart/list', {headers});
await request('/api/products/sync', {method:'POST',headers});
const es = await fetch('http://elasticsearch:9200/products/_refresh', {method:'POST'});
assert.ok(es.ok);
const countResponse = await fetch('http://elasticsearch:9200/products/_count', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({query:{range:{productId:{gte:manifest.productStart,lt:manifest.productStart+manifest.products}}}})});
assert.ok(countResponse.ok, 'Elasticsearch count failed');
const count = await countResponse.json();
assert.equal(count.count, manifest.products, 'Index cardinality mismatch');
const keywordCounts = {};
for (const word of fixtures.keywords) {
  const rows = await request(`/api/products/search?keyword=${word}&page=0&size=20`);
  assert.ok(Array.isArray(rows) && rows.length > 0, `No indexed results for ${word}`);
  keywordCounts[word] = rows.length;
}
const id = fixtures.productIds[0];
assert.equal((await request(`/api/products/${id}`, {headers})).id, id);
for (const route of [`/api/recommendations/products/${id}/similar?limit=10`, '/api/recommendations/for-you?limit=10']) {
  assert.ok(Array.isArray(await request(route, {headers})));
}
console.log(JSON.stringify({checkedAt:new Date().toISOString(),passwordLogin:true,fixtureJwt:true,indexedProducts:count.count,keywordCounts},null,2));
