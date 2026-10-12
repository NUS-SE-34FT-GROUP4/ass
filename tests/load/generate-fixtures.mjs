import fs from 'node:fs';
import crypto from 'node:crypto';
import bcrypt from 'bcryptjs';
const out = process.argv[2] || 'tests/load/generated';
const users = Number(process.env.LOAD_USERS || 2000);
const products = Number(process.env.LOAD_PRODUCTS || 10000);
if (![users, products].every(n => Number.isSafeInteger(n) && n > 1 && n <= 1000000)) throw Error('Counts must be integers from 2 to 1000000');
const password = process.env.LOAD_PASSWORD || crypto.randomBytes(18).toString('base64url');
const paymentPassword = process.env.LOAD_PAYMENT_PASSWORD || String(crypto.randomInt(0, 1000000)).padStart(6, '0');
if (Buffer.byteLength(password) < 6 || Buffer.byteLength(password) > 72) throw Error('LOAD_PASSWORD must be 6–72 bytes');
if (!/^\d{6}$/.test(paymentPassword)) throw Error('LOAD_PAYMENT_PASSWORD must be six digits');
const hash = process.env.LOAD_PASSWORD_HASH || bcrypt.hashSync(password, 10);
const paymentHash = process.env.LOAD_PAYMENT_HASH || bcrypt.hashSync(paymentPassword, 10);
for (const [plain, hashed] of [[password, hash], [paymentPassword, paymentHash]]) {
  if (!/^\$2[aby]\$(?:0[4-9]|[12]\d|3[01])\$[./A-Za-z0-9]{53}$/.test(hashed) || !bcrypt.compareSync(plain, hashed)) {
    throw Error('Supplied BCrypt hash does not match its password');
  }
}
const secret = process.env.JWT_SECRET;
const key = secret ? Buffer.from(secret, 'base64') : null;
if (key && key.length < 64) throw Error('JWT_SECRET must decode to at least 64 bytes for HS512');
const encode = x => Buffer.from(JSON.stringify(x)).toString('base64url');
function token(username) {
  if (!key) return 'REPLACE_WITH_JWT';
  const now = Math.floor(Date.now()/1000);
  const body = encode({ alg: 'HS512', typ: 'JWT' }) + '.' + encode({ sub: username, iat: now, exp: now + 7200 });
  return body + '.' + crypto.createHmac('sha512', key).update(body).digest('base64url');
}
// Reserved IDs; collisions abort INSERT, never overwrite existing records.
const userStart = 1000000;
const productStart = 1000000;
const rows = [];
const fixtures = { productIds: [], keywords: ['book', 'phone', 'chair', 'camera'], users: [] };
rows.push('START TRANSACTION;');
for (let i = 0; i < users; i++) {
  const name = `load_buyer_${String(i).padStart(4, '0')}`;
  rows.push(`INSERT INTO users (id,username,password_hash,payment_password_hash,email,balance,enabled) VALUES (${userStart+i},'${name}','${hash}','${paymentHash}','${name}@example.invalid',10000000,1);`);
  rows.push(`INSERT INTO user_roles (user_id,role_id) VALUES (${userStart+i},(SELECT id FROM roles WHERE name='ROLE_USER'));`);
  fixtures.users.push({ username: name, token: token(name), paymentPassword });
}
for (let i = 0; i < products; i++) {
  const id = productStart + i;
  const word = fixtures.keywords[i % fixtures.keywords.length];
  // Dedicated first user is seller, excluded from buyer fixture below.
  rows.push(`INSERT INTO pms_product (id,user_id,name,description,price,stock,condition_level,location,category,status) VALUES (${id},${userStart},'load ${word} ${i}','Load-test fixture',10,10000,9,'Singapore','other',1);`);
  fixtures.productIds.push(id);
}
fixtures.users.shift();
rows.push('COMMIT;');
fs.mkdirSync(out, { recursive: true, mode: 0o700 });
fs.writeFileSync(`${out}/credentials.json`, JSON.stringify({ username: fixtures.users[0].username, password, paymentPassword }, null, 2), { mode: 0o600 });
fs.writeFileSync(`${out}/manifest.json`, JSON.stringify({ users, buyers: users-1, products, userStart, productStart, bcryptCost: bcrypt.getRounds(hash), generatedAt: new Date().toISOString() }, null, 2));
fs.writeFileSync(`${out}/seed.sql`, rows.join('\n') + '\n', { mode: 0o600 });
fs.writeFileSync(`${out}/fixtures.json`, JSON.stringify(fixtures, null, 2), { mode: 0o600 });
console.log(`Generated ${users} users (${users-1} buyers), ${products} listings; tokens ${key ? 'signed for 2 hours' : 'must be supplied'}.`);
