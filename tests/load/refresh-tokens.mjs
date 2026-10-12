import fs from 'node:fs';
import crypto from 'node:crypto';
const file = 'tests/load/generated/fixtures.json';
const data = JSON.parse(fs.readFileSync(file));
const key = Buffer.from(process.env.JWT_SECRET || '', 'base64');
if (key.length < 64) throw Error('Local JWT_SECRET is missing or too short');
const encode = x => Buffer.from(JSON.stringify(x)).toString('base64url');
const now = Math.floor(Date.now()/1000);
for (const user of data.users) {
  const body = encode({alg:'HS512',typ:'JWT'})+'.'+encode({sub:user.username,iat:now,exp:now+7200});
  user.token = body+'.'+crypto.createHmac('sha512',key).update(body).digest('base64url');
}
fs.writeFileSync(file,JSON.stringify(data,null,2),{mode:0o600});
console.log(`Refreshed ${data.users.length} local fixture tokens for two hours; database unchanged.`);
