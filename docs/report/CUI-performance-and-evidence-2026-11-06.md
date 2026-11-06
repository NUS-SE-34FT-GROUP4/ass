# CUI report sections 4.1 and Chapter 6

SWE5001 ASS. Prepared 6 November 2026 from retained measurements.

## 4.1 Performance

Performance is addressed through bounded responses, separate read processing and independent service scaling. MySQL remains the source of truth for orders, balances and stock. OpenSearch provides a rebuildable listing index, while Redis holds recommendation data and other temporary state. These mechanisms reduce particular costs; their presence alone does not prove the proposal's 200-client objective.

Caching. Redis is used for precomputed recommendation scores and user interaction data. RedisCacheService also provides product-detail and user-profile cache helpers with caller-supplied expiry, but the current listing and profile read paths do not call them. We therefore do not claim a cache hit benefit for those reads. Connecting these helpers to the read path would require explicit invalidation on updates, a bounded expiry and a measured hit/miss comparison. Payment correctness continues to depend on database transactions and row locks, not cached stock or balances.

Bounded queries and responses. The product list originally returned the entire catalogue. PR #53 introduced a default page size of 12, a maximum of 100 and the X-Total-Count response header. On staging, the two-VU retest passed 77 checks with no HTTP or business errors: browse P95 was 84.46 ms and overall read P95 was 536.28 ms. The catalogue still contained 10,001 matching products. This closes the unbounded-response defect; it does not show that equally sized responses became faster or that all database work is bounded by the displayed page. In particular, keyword search obtains up to 100 candidates before Core enriches and slices them.

Load balancing. CloudFront forwards API traffic to the Application Load Balancer, whose path rules route it to separate Core, Search and Chat target groups. The ALB distributes requests among healthy tasks within the selected service. Each service can add tasks independently, so a Search burst need not also replicate Core and Chat. Direct /api/search measurements exercise Search; keyword searches through /api/products also include Core's enrichment and possible database fallback, so their latency figures are not interchangeable.

Auto scaling. The repository configuration permits 1 to 4 tasks per service. Core and Chat use a CPU target of 60%; Search uses 1,000 requests per target per minute. Scale-out and scale-in cooldowns are 60 and 300 seconds respectively. These are policy settings, not evidence that four tasks were reached. In the 18 October production search run, Search was observed to move from one to two tasks and back to one; Core and Chat were sampled at one. The owner's exported activity summary associates the transitions with the request-count policy. Chapter 6 records the result and its evidence limits.

Measured outcome and remaining work. The 18 October search-only burst completed 7,431 requests with zero HTTP or business errors and search P95 85.54 ms. The separate 19 October staging mixed retest completed its 50-VU ramp, hold and descent with read P95 1,009.17 ms and zero errors. The subsequent run targeting 200 VUs stopped during ramp-up after breaching the latency gate; sampled concurrency reached 104, so it is not a completed 200-VU result. Next steps are to profile Core's candidate enrichment and recommendation paths, control warm-up and data state, then repeat a staged mixed test and a fixed-capacity versus autoscaling comparison. AWS has since been shut down; this report uses retained measurements rather than a new live test.

## Chapter 6 performance evidence

### Performance test method and evidence

We retained k6 summaries, deployed revisions and observation records, and distinguish a low-load regression, a Search elasticity demonstration and a mixed-workload capacity attempt. VUs measure simulated concurrency; requests per second measure achieved traffic. P95 is the latency at or below which 95% of the measured requests fall, not a guarantee for every user or request. The guarded mixed tests used read median <1 second, read P95 <2 seconds and HTTP/business error rates <1%, with early abort protection. The test generator ran outside the AWS VPC, so client latency includes the network path.

### Low load pagination regression

The 14 October staging preflight used two VUs and found five failed HTTP/business checks out of 25; all five browse requests timed out at about 10 seconds. An isolated unpaged response contained 5,399,426 bytes and took 8,425 ms. After PR #53 was deployed at d838bc101a85f2d9c0aabf430f6e69eba35a3c5a, the same 30-second, two-VU configuration with one-second think time passed all 77 checks. Browse P95 was 84.46 ms, search P95 624.21 ms, overall read median 127.59 ms and read P95 536.28 ms, with zero HTTP/business errors. The endpoint still reported 10,001 matching products; the first and second pages each contained 12 items. Traffic selection was random, so this is a regression check of bounded pagination, not a controlled equal-response speedup or a capacity result.

Source: docs/performance/staging-pagination-retest/README.md, preflight.json and smoke-2vu.json; staging deployment run 38592215283.

### Production search burst and elasticity

On 18 October at 06:02:07 UTC, the production Search-only test ramped to 30 VUs over 30 seconds, held for four minutes and ramped down over 15 seconds. Anonymous book/phone queries used /api/search. It completed 7,431 requests at an average 26.00 requests/s, with zero HTTP/business errors. Search median was 28.09 ms, P95 85.54 ms and P99 413.78 ms; the maximum was 8,279.04 ms. The large maximum remains relevant even though P95 passed.

CloudWatch showed about 1,750 requests per target per minute. ECS events recorded an additional Search task starting and registering at 06:07 UTC, two desired/running tasks and two healthy targets at 06:13, and a task stopping at 06:24. At 06:26, desired and running counts were both one on the same task-definition revision 5. The observer did not change desired counts or deploy during this cycle. Core and Chat were sampled at one, rather than continuously proven to remain at one.

The console account could not read Application Auto Scaling activity history. YU subsequently supplied a summary of AWS CLI exports: three values above 1,000 (1,086, 1,664 and 1,748) triggered scale-out at 06:07:18 UTC; fifteen zero-valued minutes preceded scale-in at 06:24:09. That owner-supplied summary supports the policy cause but is not independently inspected raw activity JSON. The observed 1 to 2 to 1 cycle demonstrates elasticity for this bounded Search workload. It does not prove 200-client mixed capacity, scaling to four tasks or a performance improvement against a fixed-one-task control.

Figure 6A Search task count rising from one to two on 18 October

Figure 6B Search returning to one task after the burst

Source: docs/performance/proposal-closeout-2026-10-18/production-search-30vu.json; screenshots/search-scale-out-curve.jpg and screenshots/search-back-to-one.jpg; search-scaling-cause-owner-summary.md. These are the 18 October, 1,000-request-target records. The separate 20 October demonstration temporarily used a target of 300 and a 1-to-2 range; its settings and results are not mixed into this run.

### Mixed load failures and passing retest

On 17 October, a 50-VU mixed attempt stopped after 123.87 seconds because read P95 was 2,077.93 ms; keyword-search P95 was 2,306.82 ms. A further 19 October morning attempt stopped after 155.88 seconds, with 4,756 requests, zero HTTP/business errors, read P95 2,199.52 ms and search P95 2,319.63 ms. Successful HTTP responses therefore did not establish acceptable latency. Core logs confirmed Search timeouts and database fallback on some requests. Low OpenSearch execution latency and the client timeout/pool configuration pointed to an application-side waiting or fallback path; pool exhaustion was a hypothesis, not a measured fact.

After Search timeout and connection-pool changes in PR #75, the 19 October evening retest on revision 1b223755576dead76601856ccc7d17e473625512 completed its 60-second ramp, 120-second hold and 15-second descent at 50 VUs. It recorded 6,725 requests at 34.35 requests/s, zero HTTP/business errors, read median 303.45 ms, read P95 1,009.17 ms and search P95 688.34 ms. All configured gates passed. The workload also passed 206 payment checks, 216 cart sequences and 212 WebSocket self-message checks; these are not a ledger audit or paired-user delivery validation. Random request selection and other revision changes limit attribution of the improvement to PR #75 alone.

The subsequent attempt targeting 200 VUs stopped after 31.47 seconds during the ramp, at a sampled peak of 104 VUs. It recorded 795 requests at 25.26 requests/s, three HTTP failures (0.377%) and read P95 5,041.87 ms; search P95 was 3,245.75 ms and similar-recommendation P95 was 6,393.32 ms. There was no steady hold at 200. Core's one-minute CPU maximum reached 100%, but its average was 63.1%, and a new task had only recently started. These observations support investigating Core pressure, candidate enrichment and warm-up; they do not establish a single proven bottleneck. Timed-out payments had unknown server outcomes and must not be interpreted as no debit.

The remaining capacity work is to profile the Core and recommendation paths, batch repeated lookups where appropriate, then repeat staged mixed loads with the same revision, fixture, pacing and warm-up conditions. A separate fixed-capacity versus autoscaling comparison is still needed. No new cloud test was run for this report because the AWS environments have been shut down.

Sources: docs/performance/staging-2026-10-17/mixed-50vu-aborted.json; docs/performance/staging-2026-10-19/README.md; docs/performance/staging-2026-10-19/retest-evening/{smoke-summary.json,mixed50-summary.json,mixed200-summary.json,README.md}. The evening 50-VU window was 11:57:27-12:00:43 UTC on 19 October; the 200-target attempt was 12:05:56-12:06:27 UTC. The integration, oversell and paired WebSocket evidence follows in the existing subsections.
