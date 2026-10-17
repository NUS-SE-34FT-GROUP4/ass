# Staging guarded load check — 17 October 2026

Owner: CUI ZIJIAN. Target: https://dxjzlpbh4ve2y.cloudfront.net.
GitHub deployment run 38918373788 completed staging deployment/smoke for commit
6f5f99033c37604f6f692ba0b936c261d00686ce. ECS image digests were not independently read.
The user confirmed an exclusive test window without merges/deployments.
No PR was merged and no infrastructure configuration was changed during testing.

Local Docker k6 2.3.0 ran under macOS caffeinate -i. Private fixtures contain 1999 buyers
and 10000 product IDs. Tokens remained valid throughout the run; credentials are excluded.
A local ignored wrapper preserved the workload and added abortOnFail after 30 seconds
for HTTP/business error rate >=1%, read median >=1000 ms or read p95 >=2000 ms.
These cumulative gates stop an unhealthy run; they do not prove steady-state capacity.

## Results

- Read-only smoke: 2 VUs for 30 seconds, 67 HTTP requests, no failures, read p95 465.89 ms.
- Mixed stage: planned 60-second ramp to 50 VUs, 120-second hold, 15-second descent.
  Automatically aborted at 123.87 seconds; 50 iterations interrupted. Container exited.
- Mixed run ended 2026-10-17 02:59:47 UTC / 10:59:47 Singapore, starting approximately
  02:57:43 UTC. No 200-VU stage was started.
- Completed HTTP requests: 3763, HTTP/business failure rate 0%. Read median 310.08 ms,
  read p95 **2077.93 ms**, exceeding the configured two-second gate.
- Endpoint p95: search 2306.82 ms; similar recommendations 1499.77 ms; personalized
  recommendations 1393.07 ms; cart list 702.35 ms; detail 588.98 ms; browse 495.47 ms.
- Completed checks include 96 purchase/payment sequences and 112 self-recipient chat
  round trips. No account balances, order identifiers or credentials are published.

## Interpretation and next steps

This stage failed the latency gate despite zero failures among completed requests.
Interrupted operations are not counted as successful completed flows. Test writes remain
in the disposable dataset; audit side effects before resetting or replaying transactions.
Unlike the previous interrupted run, elapsed time is normal and tokens were valid.

Search was the slowest measured read operation. This does not identify the cause:
inspect Core-to-Search timeout/fallback logs, Search/Elasticsearch health, ECS CPU/memory
and task counts for 02:57–03:01 UTC. Do not infer fallback or successful autoscaling from
client latency alone. No 1-to-4 scaling, 200-VU acceptance or full ledger/stock audit is claimed.
Keep the existing performance target; investigate and coordinate changes with the environment
owner before a repeat run. No frozen application code was modified.
