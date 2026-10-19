# CUI architecture defence and rehearsal card

Reframed on 19 October 2026 against **01 Briefing.pdf**, Architecting Scalable Systems practice module, V2.6. All measured results refer to ass (5001), not c2cTradePlatform (5006). Presentation language: English.

## What the briefing actually asks us to demonstrate

Pages 3 and 5 emphasise scalable, robust and reliable backend architecture; platform engineering; appropriate cloud-native services; development/deployment automation; and minimum security controls. A working application demonstrates platform capabilities. Page 9 explicitly asks what scale is supported, how claims are proved, what cloud-native value is achieved, how new use cases reuse the platform, and what lifecycle work is automated. These are stated objectives and questions, not an invented detailed marking rubric.

The marketplace provides a concrete workload and domain context. Do not make feature breadth the central claim. Equally, do not say functionality is irrelevant: selected working flows must exercise the architecture being defended. A broken message path matters if it is used to demonstrate cross-instance communication. Our self-recipient load checks do not establish buyer-to-seller delivery.

CUI's contribution is to connect **quality requirement → architectural choice → measured evidence → trade-off and limit**. The team owns the full architecture narrative; CUI supplies performance, observability and validation evidence. Service owners primarily implement their unit/service tests; CUI reports coverage honestly rather than claiming sole responsibility for the target.

## Main speaking script — approximately three minutes, excluding UI operation

### P14 — bound work before scaling it

“My part focuses on how we evaluated the architecture's performance and scalability. The marketplace is the workload we use to exercise the platform.

Our first finding was that adding instances would not address an unbounded request. At just two concurrent users, the product-list endpoint returned more than ten thousand items in one response and took over eight seconds. Server-side pagination bounded the response work. In the low-load retest, all seventy-seven checks passed and browse P95 was eighty-four milliseconds. This validates the pagination fix under that workload; it does not establish maximum platform capacity.”

### P19 — independent scaling and observable cloud behaviour

“We separated Core, Search and Chat around different workload profiles and transaction responsibilities. This gives us a way to scale a busy service independently, rather than duplicate the entire application. The cost is additional deployment, communication and operational complexity.

To exercise Search independently, our production test called the Search route directly. We used thirty virtual users and completed seven thousand four hundred and thirty-one requests, with no HTTP or business errors and a P95 of about eighty-six milliseconds.

The captured task counts changed from one to two and then back to one. Core and Chat were each at one in sampled observations. We made no manual scaling changes during this run. This provides observed evidence of Search elasticity following the burst. YU subsequently supplied the activity and alarm-history summary: three consecutive minutes above one thousand requests per target triggered scale-out; fifteen minutes below nine hundred triggered scale-in. This is not proof of two-hundred-user capacity or linear scaling.

The monitoring evidence matters because a successful HTTP response alone cannot prove that scaling occurred. We correlate client latency and errors with request-rate graphs, task counts and service events.”

### P20 — correctness across instances and honest limits

“Scaling also needs to preserve correctness. On staging, two buyers attempted to buy the last unit. The owner-provided load-balancer records show different Core instances handling the payments, with about thirty-one milliseconds of overlapping processing. One payment succeeded, the other was rejected for insufficient stock, and final stock was zero. The owner's database check confirmed one transaction and no debit to the rejected buyer.

This is a small cross-instance correctness test, not a high-concurrency benchmark. The rejected request returned HTTP five hundred, which remains an error-status limitation.

We distinguish measured results from unfinished targets. The separate fifty-user mixed staging run exceeded its latency threshold, and the two-hundred-user capacity test and fixed-instance comparison are unfinished. CI reported one hundred and forty-nine passing tests, but measured service-layer coverage was eighteen point six seven percent, below the proposed seventy percent. Our conclusion is that we have evidence for bounded request handling, observed Search elasticity, and one cross-instance stock-consistency scenario, with clear limits on the claims.”

## Evidence to show, and why

| Architectural question | Show | Defensible conclusion |
|---|---|---|
| Does each request have bounded work? | P14 pagination incident and retest | The specific unbounded-list defect was corrected; no universal capacity claim |
| Can the service footprint change independently? | P19 baseline, request rate, two-task state, return-to-one screenshots | Search 1 → 2 → 1 observed; Core/Chat sampled at one; policy cause supported by YU’s export summary |
| Does a write invariant survive multiple instances? | A2 success/rejection/stock screenshots and owner log/database summary | One bounded two-buyer scenario preserved stock and charge outcomes |
| Can we detect an unacceptable result? | 50-VU latency-gate failure and monitoring | Zero errors alone is insufficient; latency can fail acceptance |
| Are quality claims auditable? | P20 CI and coverage evidence | Passing tests, coverage and capacity are different measures |

Use saved evidence first. The optional short command below demonstrates repeatable tooling, not autoscaling. A full storefront tour is the team's application demonstration; CUI should explain the architecture exercised by a selected flow instead of narrating every button.

## Likely architecture questions and answers

**Why these three services rather than a monolith or many small services?**
Core keeps closely coupled business writes within one service transaction, while Search and Chat have different load and connection patterns. The split permits independent scaling. A monolith is operationally simpler; finer splitting adds distributed transaction and failure-handling work. Three services are a pragmatic project boundary, not a universal optimum.

**What remains a scaling bottleneck?**
The services still share a database instance/schema. Scaling stateless application tasks does not remove database contention, connection limits or expensive queries. Separate service database users improve access isolation, not storage or capacity isolation. Database behaviour and longer steady-state load need further measurement before claiming a capacity ceiling.

**What value does the cloud add?**
Fargate reduces host management; managed persistence services reduce operational work; load balancing and service scaling provide elasticity mechanisms. Our measured example is the Search task-count cycle. Costs include managed-service expense, startup delay, quotas and provider dependence. We have not measured cost savings.

**Why is Search a separate read model?**
It can serve search-oriented queries and evolve independently from authoritative business records. The cost is eventual consistency and index-rebuild/failure handling. Do not imply the search index is the source of truth or that an outage/fallback test was performed by CUI in this run.

**How does platform engineering matter beyond this marketplace?**
Accounts, listing/search APIs and messaging provide candidate reusable capabilities for additional applications. Explain the concrete APIs and producer/consumer roles in the team's design. A new use case could reuse those capabilities, but we have not measured implementation-effort savings or proven reuse across multiple deployed applications.

**Does A2 prove the locking mechanism is correct under all races?**
No. It demonstrates the observed invariant in one two-instance, two-buyer scenario, supported by owner-reported routing and database checks. It does not isolate the internal locking mechanism or prove repeated/high-load correctness. The 31 ms request-processing overlap does not prove overlap inside the inventory critical section.

**Is cross-instance messaging proven by your load test?**
No. Our load checks used a self-recipient on one connection. Paired-user delivery, offline recovery and multiple-instance routing need separate evidence. Attribute any teammate's independent test accurately; do not substitute the self-recipient count for that evidence.

**Can the platform support 200 concurrent users?**
Not established by our completed tests. VUs are a workload setting, not a universal supported-user count. The planned long run and fixed-one-task comparison remain incomplete. Thirty-VU Search and fifty-VU mixed staging results differ in workload/environment and are not a controlled before/after comparison.

**How does automation help?**
Repeatable load scripts, CI tests/scanning and the deployment pipeline reduce manual variation and provide traceable results. Show the six-job pipeline already described in P17; SonarCloud is inside the backend job. Production promotion uses manual approval. We have not quantified engineering-hours saved.

**Why is coverage below target despite passing CI?**
Passing tests establish only the tested assertions. Service-layer coverage is below the proposed target, and the configured SonarCloud gate is a different criterion. Treat this as a team quality gap, not proof that every untested path is broken or that every workflow is verified.

## Numbers to quote

| Evidence | Result | Meaning / limit |
|---|---|---|
| Pagination pre-flight | 25 requests, 5 failures; one response 10,001 items / 5,399,426 bytes / 8,425 ms | Unbounded product list; single response time differs from browse P95 10,006 ms |
| Pagination retest, 2 VUs | 77 checks passed; browse P95 84 ms; search P95 624 ms; overall read median 128 ms / P95 536 ms | Low-load pagination validation, not single-user or capacity evidence |
| Staging 50 VUs, 17 Oct | Stopped after about 124 s; read P95 2,078 ms; search P95 2,307 ms; HTTP/business errors 0 | Latency threshold failed; cause was not established |
| Production search burst, 18 Oct | 30 VUs; 7,431 requests; errors 0; P95 85.54 ms; max 8,279.04 ms | Different environment and workload; not a controlled improvement over the 50-VU mixed run |
| Production Search tasks | 1 → 2 → 1; extra task start 14:07, task stop 14:24 Singapore | Sampled Core/Chat counts stayed at 1; policy cause supplied by YU; see search-scaling-cause-owner-summary.md |
| CI tests | 149 passed: Core 84 / Search 40 / Chat 25 | Zero failures, errors, skips on measured revision c3f8a43 |
| Service-layer line coverage | 18.67% overall; Core 12.82%, Search 89.83%, Chat 1.23% | 70% proposal target not met; not enforced by a separate CI rule |
| Whole-module line coverage | 23.98% overall | Different denominator from service-layer coverage |

## One-line live demonstration command

Run from the repository root with k6 installed, only after the current demonstration window is agreed. This is a short anonymous read-only search demonstration. It needs no token and uses the non-secret example fixture's book/phone keywords. It does not read or mutate the fixture product IDs in search mode.

```sh
k6 run -e BASE_URL=https://d28v51gtznejrx.cloudfront.net -e MODE=search -e SEARCH_PATH=/api/search -e VUS=2 -e RAMP=5s -e HOLD=20s -e DOWN=5s --summary-export=/tmp/ass-demo-search-summary.json tests/load/marketplace.js
```

The scheduled ramp/hold/down lasts 30 seconds; graceful completion may take longer. This command retains the script's end-of-run thresholds; it does not add early-abort protection. If the demo fails, stop with Ctrl+C and use saved evidence. Do not increase VUs or retry repeatedly on stage. Two VUs are not expected to demonstrate scale-out: show the previously captured 1 → 2 → 1 evidence separately. No new production run was executed while preparing this card.

## Delivery and evidence checklist

- Report and screenshots: https://github.com/NUS-SE-34FT-GROUP4/ass/pull/71
- CI evidence: https://github.com/NUS-SE-34FT-GROUP4/ass/actions/runs/39040174201
- Search screenshot order under `screenshots/`: search-baseline.jpg, search-request-rate.jpg, search-scale-out-curve.jpg, search-2-running.jpg, search-back-to-one.jpg.
- A2 raw screenshots are retained privately. The owner supplied log/database conclusions through the user; the raw exported logs have not been delivered to this workspace. Do not claim independent raw-log inspection.
- The user confirmed Core capacity restoration and access logging disabled. No further A2 run is planned.
- Production account names supplied: demo_buyer and seller_lvl1 through seller_lvl5. Select the seller owning the demonstration item; credentials must stay outside this document and Git.
- Team-owned items: final demo operator assignment, seller/item pairing, fallback recording and rehearsal timing. Do not reseed products that have orders.
- No original load-test video was captured; label a later screenshot walkthrough as retrospective evidence review.
- P19/P20 notes and body were updated separately. This revised card does not silently change other speakers' assignments or replace the shared slides.

Briefing page 6 labels its presentation dates tentative; use the team's current confirmed schedule. Do not infer a new deadline from that slide.
