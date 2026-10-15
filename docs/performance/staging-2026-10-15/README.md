# Staging pre-freeze checks — 15 October 2026

Owner: CUI ZIJIAN. Target: https://dxjzlpbh4ve2y.cloudfront.net (5001 / ass).
Generator: local Docker k6 2.3.0, outside the VPC. Running images, revision, task counts
and applied scaling policies were not independently verified. This is low-concurrency
functional evidence, not full capacity or scaling acceptance.

## Preparation

Private fixtures contain 1999 buyers, 10000 product IDs and four keywords. Sample
account and product endpoints responded successfully. This does not independently verify
full database/index cardinality. Credentials and account/order financial readbacks are
excluded from this report. Preflight evidence retains endpoint patterns, status and timing only.

## Runs

Common settings: one-second think time, 10-second HTTP timeout, private fixtures,
DEPLOYMENT=staging-scaling-unverified, REVISION unknown.

| Run | Parameters | End UTC | Result |
|---|---|---|---|
| Read smoke | MODE=smoke, VUS=2, HOLD=30s | 15:13:07 | 62 HTTP requests, no failures; read p95 1090.72 ms |
| Mixed check | MODE=mixed, ENABLE_WRITES=true, VUS=2, RAMP=5s, HOLD=60s, DOWN=5s | 15:14:51 | 168 HTTP requests, no HTTP/business failures; read p95 187.14 ms |

All configured aggregate thresholds passed. Smoke uses constant VUs; generic ramp/down
summary fields do not apply. Search-only smoke p95 was **2163.25 ms** from 11 requests,
above two seconds despite the aggregate gate passing. Do not claim all endpoints met
the goal. The later mixed run is not a controlled before/after comparison.

Mixed aggregate checks covered five cart sequences, four purchase/payment sequences and
three matching chat round trips without failed checks. No account identifiers, order
identifiers or balances are published. Writes remain in the disposable dataset; coordinate
reset before matched comparisons. No data reset was performed during this run.

Chat uses a self-recipient on the same connection, not cross-instance recipient delivery.
Chat p95 was 549.9 ms from only three samples and is not a stable latency estimate.
No complete stock/ledger audit, refund/fulfilment, oversell or autoscaling pass is claimed.

## Remaining work

Confirm apply, index rebuild, running images and scaling state with YU. Complete dedicated
stock/ledger and paired-user chat checks before controlled capacity/scaling comparisons.
Retain matching CloudWatch windows and scaling activity records. No application change
was required by these checks; search latency still needs follow-up.
