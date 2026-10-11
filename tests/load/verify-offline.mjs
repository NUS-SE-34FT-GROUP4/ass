import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
const root=new URL('./', import.meta.url);
const fixture={productIds:[1],keywords:['book'],users:Array.from({length:20},(_,i)=>({username:`buyer${i}`,token:'valid-token',paymentPassword:'123456'}))};
let checks=0;
function context(mode='mixed',fail=false,roll=0.1) {
  const metrics={}; const calls=[];
  class Metric {constructor(name){this.name=name;metrics[name]=[];} add(v){metrics[this.name].push(v);}}
  const user=fixture.users[0];
  const response=(status,value)=>({status,json:key=>key?value[key]:value});
  const c={__ENV:{ENABLE_WRITES:'true',PRODUCT_ID:'1',INITIAL_STOCK:'1',BUYERS:'20'},__VU:1,__ITER:0,
    base:'http://mock', mode, data:fixture,configuredOptions:{},
    Rate:Metric,Counter:Metric,Trend:Metric,sleep:()=>{},
    check:(r,pred)=>Object.values(pred).every(fn=>fn(r)),
    Math:Object.assign(Object.create(Math),{random:()=>roll}),Date,JSON,console,
    http:{request:(method,url,body)=>{
      calls.push({method,url,body});
      if(fail)return response(200,{success:false});
      if(url.includes('buy-now'))return response(200,{id:7});
      if(url.endsWith('/pay'))return response(200,{status:'PAID'});
      if(method!=='GET')return response(200,{success:true});
      if(/\/products\/1$/.test(url))return response(200,{id:1,stock:1});
      return response(200,[{id:1}]);
    }},
    ws:{connect:(url,p,cb)=>{
      assert.match(url,/\/ws\/000\/load-1-0\/websocket$/);
      const events={};const socket={on:(n,f)=>events[n]=f,setTimeout:()=>{},close:()=>{},send:encoded=>{
        const msg=JSON.parse(encoded)[0];
        if(msg.startsWith('CONNECT')){assert.match(msg,/Authorization:Bearer valid-token/);events.message('a'+JSON.stringify(['CONNECTED\n\n\0']));}
        if(msg.startsWith('SUBSCRIBE'))events.message('a'+JSON.stringify(['RECEIPT\nreceipt-id:subscribed\n\n\0']));
        if(msg.startsWith('SEND')){const body=JSON.parse(msg.split('\n\n')[1].slice(0,-1));assert.equal(body.recipient,user.username);events.message('a'+JSON.stringify(['MESSAGE\n\n'+JSON.stringify(body)+'\0']));}
      }};
      cb(socket);events.message('o');return {status:101};
    }},
  };
  vm.createContext(c);
  const src=fs.readFileSync(new URL('marketplace.js', root),'utf8').replace(/^import .*;\r?\n/gm,'').replace(/export const options = configuredOptions;/,'const options = configuredOptions;').replace('export default function ()','function iteration()').replace(/export function /g,'function ');
  vm.runInContext(src,c);return {c,metrics,calls};
}
for(const [name,roll] of [['browse',.1],['search',.4],['detail_recommendation',.7],['cart',.87],['chat',.92],['purchase',.98]]){
  const {c,metrics,calls}=context('mixed',false,roll);c.setup();c.iteration();
  assert.ok(metrics.business_errors.length>0);assert.ok(metrics.business_errors.every(v=>v===false));
  if(name==='purchase')assert.equal(calls[1].url,'http://mock/api/orders/7/pay');
  if(name==='chat')assert.equal(metrics.chat_roundtrip_ms.length,1);
  checks++;
}
const failure=context('mixed',true,.87);failure.c.iteration();assert.deepEqual(failure.metrics.business_errors,[true]);assert.equal(failure.calls.length,1);checks++;
const broken=context('mixed');broken.c.__ENV.ENABLE_WRITES='false';assert.throws(()=>broken.c.setup(),/ENABLE_WRITES/);checks++;
const smoke=context('smoke',false,.98);smoke.c.iteration();assert.ok(smoke.calls.every(r=>r.method==='GET'));checks++;
const search=context('search',false,.98);search.c.iteration();assert.equal(search.calls.length,1);assert.match(search.calls[0].url,/products\/search/);checks++;
console.log(`${checks} offline behavior checks passed (mock modules; no network or server performance tested).`);
