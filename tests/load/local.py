#!/usr/bin/env python3
"""Reproducible, isolated WP10 local validation (Python 3 + Docker only).
Never removes volumes, resets a database, or contacts a shared application.
"""
import argparse
import base64
import datetime
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
LOAD = ROOT / 'tests/load'
PROJECT = os.environ.get('WP10_PROJECT', 'ass-wp10-local')
COMPOSE = ['docker', 'compose', '-p', PROJECT, '-f', str(LOAD / 'compose.local.yaml')]
RESULTS = LOAD / 'results'

def command(args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, text=True, **kwargs)

def compose(*args, **kwargs):
    return command(COMPOSE + list(args), **kwargs)

def sql(query=None, file=None):
    args = ['exec', '-T', '-e', 'MYSQL_PWD=local-wp10-root', 'mysql', 'mysql', '-uroot', '-N', '-B', 'trade']
    if file:
        with file.open() as stream:
            return compose(*args, stdin=stream, capture_output=True).stdout
    return compose(*args, input=query, capture_output=True).stdout.strip()

def init():
    RESULTS.mkdir(exist_ok=True)
    env = LOAD / 'local.env'
    if not env.exists():
        env.write_text('JWT_SECRET=' + base64.b64encode(secrets.token_bytes(64)).decode() + '\n')
        env.chmod(0o600)

def build():
    command(['docker', 'run', '--rm', '-v', f'{ROOT}:/app', '-v', 'ass-wp10-maven:/root/.m2', '-w', '/app',
             'maven:3.9-amazoncorretto-17', 'mvn', '-B', '-ntp', 'install'])
    command(['docker','run','--rm','-v',f'{ROOT}:/app','-w','/app/tests/load','node:22-alpine','npm','ci','--ignore-scripts','--no-audit','--no-fund'])
    command(['docker','run','--rm','-v',f'{ROOT}:/app','-w','/app/tests/load','node:22-alpine','npm','test'])
    compose('build','minio','backend')

def up():
    compose('up','-d','mysql','redis','rabbitmq','elasticsearch','minio','backend')
    deadline = time.monotonic() + 300
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen('http://127.0.0.1:18080/actuator/health', timeout=5) as r:
                if json.load(r).get('status') == 'UP':
                    print('Real backend is healthy')
                    return
        except Exception:
            time.sleep(2)
    raise RuntimeError('Backend did not become healthy; inspect docker compose logs')

def seed():
    # Fail before generating or importing anything if this database already
    # contains load fixtures. Never silently reuse old JWTs or overwrite data.
    assert sql('SELECT COUNT(*) FROM users WHERE id >= 1000000;') == '0', 'Seed already exists; preserve it or explicitly use a fresh environment'
    assert sql('SELECT COUNT(*) FROM pms_product WHERE id >= 1000000;') == '0', 'Product ID range is occupied'
    assert sql("SELECT COUNT(*) FROM roles WHERE name='ROLE_USER';") == '1'
    compose('run','--rm','-T','node','tests/load/generate-fixtures.mjs')
    sql(file=LOAD/'generated/seed.sql')
    manifest_file = LOAD/'generated/manifest.json'
    manifest = json.loads(manifest_file.read_text())
    manifest['project'] = PROJECT
    manifest_file.write_text(json.dumps(manifest,indent=2)+'\n')
    verify_data()

def verify_data():
    m = json.loads((LOAD/'generated/manifest.json').read_text())
    assert m.get('project') == PROJECT, 'Fixture files belong to another project; restore the matching generated/ directory'
    u,p = m['users'],m['products']
    assert int(sql(f'SELECT COUNT(*) FROM users WHERE id >= 1000000 AND id < {1000000+u};')) == u
    assert int(sql(f'SELECT COUNT(*) FROM pms_product WHERE id >= 1000000 AND id < {1000000+p} AND user_id=1000000;')) == p
    assert int(sql(f"SELECT COUNT(*) FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id >= 1000000 AND ur.user_id < {1000000+u} AND r.name='ROLE_USER';")) == u
    return m

def check():
    m = verify_data()
    compose('run','--rm','-T','node','tests/load/refresh-tokens.mjs',capture_output=True)
    captcha_id,captcha_code = secrets.token_hex(12),secrets.token_hex(3)
    compose('exec','-T','redis','redis-cli','SET','captcha:'+captcha_id,captcha_code,'EX','120',capture_output=True)
    result = compose('run','--rm','-T','-e','CAPTCHA_ID='+captcha_id,'-e','CAPTCHA_CODE='+captcha_code,
                     'node','tests/load/preflight.mjs',capture_output=True)
    evidence = json.loads(result.stdout)
    evidence.update({'seedUsers':m['users'],'seedProducts':m['products'],'rolesVerified':True})
    (RESULTS/'preflight.json').write_text(json.dumps(evidence,indent=2)+'\n')
    print(json.dumps(evidence,indent=2))

def audit():
    m = verify_data()
    # Verify every seeded buyer's balance, not only a global total.
    violations = {
        'buyerBalances': int(sql("SELECT COUNT(*) FROM users u LEFT JOIN (SELECT user_id,SUM(total_amount) amount FROM orders WHERE status='PAID' GROUP BY user_id) o ON u.id=o.user_id WHERE u.id>=1000000 AND u.balance <> 10000000-COALESCE(o.amount,0);")),
        'fixtureStock': int(sql(f"SELECT COUNT(*) FROM pms_product p LEFT JOIN (SELECT i.product_id,SUM(i.quantity) qty FROM order_items i JOIN orders o ON o.id=i.order_id WHERE o.status='PAID' GROUP BY i.product_id) x ON x.product_id=p.id WHERE p.id>=1000000 AND p.id<{1000000+m['products']} AND p.stock<>10000-COALESCE(x.qty,0);")),
        'paymentTransactions': int(sql("SELECT COUNT(*) FROM orders o LEFT JOIN (SELECT order_id,COUNT(*) n,SUM(amount) amount FROM transactions WHERE status='SUCCESS' AND transaction_type='PAYMENT' GROUP BY order_id) t ON t.order_id=o.id WHERE (o.status='PAID' AND (COALESCE(t.n,0)<>1 OR t.amount<>o.total_amount OR o.payment_method<>'balance')) OR (o.status<>'PAID' AND COALESCE(t.n,0)<>0);")),
        'negativeStock': int(sql('SELECT COUNT(*) FROM pms_product WHERE stock<0;')),
    }
    report={'checkedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'violations':violations,
            'paidOrders':int(sql("SELECT COUNT(*) FROM orders WHERE status='PAID';")),
            'payments':sql("SELECT COUNT(*),COALESCE(SUM(amount),0) FROM transactions WHERE status='SUCCESS' AND transaction_type='PAYMENT';")}
    (RESULTS/'accounting-audit.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))
    assert not any(violations.values()), 'Persisted accounting audit failed'

def evidence(label):
    stamp = datetime.datetime.now(datetime.timezone.utc).isoformat()
    # Explicit aggregate queries avoid recording tokens, passwords or PII.
    data = {'timestamp':stamp,'project':PROJECT,'gitRevision':command(['git','rev-parse','HEAD'],capture_output=True).stdout.strip(),
            'workingTree':command(['git','status','--short'],capture_output=True).stdout,
            'counts':sql('SELECT (SELECT COUNT(*) FROM users WHERE id>=1000000), (SELECT COUNT(*) FROM pms_product WHERE id>=1000000), (SELECT COUNT(*) FROM orders), (SELECT COUNT(*) FROM transactions), (SELECT COUNT(*) FROM chat_message);'),
            'orders':sql('SELECT status,COUNT(*),SUM(total_amount) FROM orders GROUP BY status;'),
            'stock':sql('SELECT MIN(stock),SUM(stock) FROM pms_product WHERE id>=1000000;'),
            'balances':sql('SELECT SUM(balance) FROM users WHERE id>=1000000;'),
            'containers':compose('ps','--format','json',capture_output=True).stdout,
            'resources':command(['docker','info','--format','{{.Architecture}} {{.NCPU}} CPUs {{.MemTotal}} bytes RAM'],capture_output=True).stdout,
            'images':compose('images','--format','json',capture_output=True).stdout}
    (RESULTS/f'{label}-evidence.json').write_text(json.dumps(data,indent=2)+'\n')

def oversell():
    # Dedicated listing, never part of the sustained-load fixture array.
    assert sql('SELECT COUNT(*) FROM pms_product WHERE id=2000000;') == '0', 'Dedicated oversell product already exists; do not reuse its stock'
    compose('run','--rm','-T','node','tests/load/refresh-tokens.mjs',capture_output=True)
    sql("INSERT INTO pms_product(id,user_id,name,description,price,stock,condition_level,location,category,status) VALUES (2000000,1000000,'load oversell single unit','Dedicated concurrency test',10,1,9,'Singapore','other',1);")
    label = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-oversell'
    evidence(label+'-before')
    args=['run','--rm','-T','k6','run','--no-color','--summary-export',f'tests/load/results/{label}.json',
          '-e','BASE_URL=http://backend:8080','-e','FIXTURES=./generated/fixtures.json',
          '-e','ENABLE_WRITES=true','-e','PRODUCT_ID=2000000','-e','INITIAL_STOCK=1','-e','BUYERS=20',
          'tests/load/oversell.js']
    try:
        with (RESULTS/f'{label}.log').open('w') as log:
            compose(*args,stdout=log,stderr=subprocess.STDOUT)
    finally:
        evidence(label+'-after')
        audit()
    assert sql('SELECT stock FROM pms_product WHERE id=2000000;') == '0'
    assert sql("SELECT COUNT(*) FROM orders o JOIN order_items i ON i.order_id=o.id WHERE i.product_id=2000000 AND o.status='PAID';") == '1'
    print('Oversell verified: 20 concurrent buyers, 1 paid order, stock 0, balances and transactions consistent')

def run(mode, vus, ramp, hold, down):
    check()
    label = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+mode
    evidence(label+'-before')
    revision = command(['git','rev-parse','HEAD'],capture_output=True).stdout.strip()
    options={'BASE_URL':'http://backend:8080','FIXTURES':'./generated/fixtures.json','MODE':mode,
             'VUS':str(vus),'RAMP':ramp,'HOLD':hold,'DOWN':down,'ENABLE_WRITES':'true' if mode=='mixed' else 'false',
             'DEPLOYMENT':PROJECT+'-core-1','REVISION':revision,'SUMMARY':f'tests/load/results/{label}.json'}
    args=['run','--rm','-T','k6','run','--no-color']
    for key,value in options.items(): args += ['-e',f'{key}={value}']
    args += ['tests/load/marketplace.js']
    try:
        with (RESULTS/f'{label}.log').open('w') as log:
            compose(*args,stdout=log,stderr=subprocess.STDOUT)
    finally:
        evidence(label+'-after')
        with (RESULTS/f'{label}-backend.log').open('w') as log:
            compose('logs','--no-color','--tail','1000','backend',stdout=log,stderr=subprocess.STDOUT)
    print(f'Result saved: {RESULTS / (label+".json")}')

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['build','up','seed','check','run','evidence','audit','oversell'])
    parser.add_argument('--mode',choices=['smoke','mixed','search'],default='smoke')
    parser.add_argument('--vus',type=int,default=2)
    parser.add_argument('--ramp',default='30s')
    parser.add_argument('--hold',default='30s')
    parser.add_argument('--down',default='10s')
    args=parser.parse_args()
    init()
    if args.action=='run': run(args.mode,args.vus,args.ramp,args.hold,args.down)
    elif args.action=='evidence': evidence('manual')
    else: globals()[args.action]()
