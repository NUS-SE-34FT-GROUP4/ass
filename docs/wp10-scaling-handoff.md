# WP10 scaling handoff to YU

Status updated 15 October 2026: the dashboard is deployed and a historical screenshot is available. YU, through CUI, confirmed that staging autoscaling is being enabled. Completion of apply, dataset reload and index rebuild is still pending confirmation. The settings below are agreed targets, not independently verified live settings or measured optimal values.

| Service | Metric | Target | Minimum / maximum tasks |
|---|---|---|---|
| Core | Average ECS CPU | 60% | 1 / 4 |
| Search | ALB requests per target per minute | 1000 | 1 / 4 |
| Chat | Average ECS CPU | 60% | 1 / 4 |

Scale-out cooldown: 60 seconds. Scale-in cooldown: 300 seconds. These retain the Terraform defaults. YU confirmed the request-count policy for Search; 1000 remains a starting point requiring measurement. ALB HTTP request counts do not fully represent Chat connections or message throughput, so Chat retains its CPU policy.

Autoscaling defaults to enabled in production and disabled in staging. Staging requires explicit `enable_autoscaling=true`. The 1–4 range is a capacity bound, not a promise to jump immediately to four tasks when CPU exceeds the target. Check quotas, image health and task startup time.

## Deployment and acceptance

YU runs Terraform plan in the correct AWS account and workspace, reviews the intended dashboard and infrastructure changes, and applies them through the team process. The `wp10_dashboard_name` output identifies the dashboard. CloudWatch resources may incur costs; local validation does not deploy them.

The dashboard covers service CPU/memory, desired/running tasks, ALB p50/p95 latency, requests per target and target 5xx. Container Insights is enabled in the cluster configuration. Missing data is not zero: verify target-group dimensions, traffic and metric permissions. ALB latency is in seconds, while k6 latency is reported in milliseconds. Use k6 for WebSocket message latency.

Start with a low-load staging smoke test, then increase load gradually while recording task counts, CPU, p95, error rates and scaling timings. Check database, connection-pool and search bottlenecks; low CPU alone is not sufficient reason to increase concurrency. Stop the generator after testing, verify recovery and scale-in, and record capacity/cost observations.

## Evidence still required

- Complete dashboard evidence and the actual deployment workspace. The existing partial screenshot does not cover all service charts.
- Matched-workload p95/error comparisons and task-count timelines before and during scaling.
- Measured justification for the Search target of 1000 requests per target per minute.
- The old single-Core baseline is historical context only; it does not establish split-service performance or AWS autoscaling success.

Metric references: [ALB metrics](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-cloudwatch-metrics.html), [ECS Container Insights](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Container-Insights-metrics-ECS.html).

## Confirmed freeze arrangements — 15 October

Source: YU/Chenglin's confirmation relayed by CUI. Application code freezes on **16 October 2026**.

- All three services use a target capacity range of 1–4, with the policies and cooldowns above. YU will notify CUI when apply completes.
- The previous load dataset was cleared. After apply, YU will reload 2000 accounts and 10000 products, with IDs starting at 1000000, and rebuild the search index. Earlier samples do not prove the reload is complete.
- When the test is ready to start, YU will issue tokens valid for two hours and deliver them privately. Keep credentials in ignored directories; never commit tokens, passwords or signing keys.
- Documentation, screenshots and test-evidence PRs may merge after the application code freeze. Send measured Terraform tuning recommendations to YU to change and apply; infrastructure configuration changes are handled separately from the application code freeze.
- Bringing up production from 16 October does not prevent continued staging tests. All load generators must explicitly target staging.

### Accepted Redis architecture limitation

Only Core currently uses Redis; Chat and Search do not connect to it. Core keys lack a uniform service prefix, so the proposal's service-namespacing convention remains unmet. With the current service usage, there is no cross-service key collision. This is an acknowledged architectural limitation, not a claim that namespace isolation has been implemented. No code change is planned before the freeze.

If another service adopts Redis, introduce service prefixes or separate logical databases and plan migration of caches, locks and expiring keys. Logical database separation is not security permission isolation.

### Next execution sequence

1. Wait for YU to confirm apply, dataset import and index rebuild. Record actual images, task counts, policies and deployed revision.
2. Confirm the generator and monitoring are ready, then request fresh fixtures/tokens. Check expiry and sample accounts, products, stock and balances.
3. Run low-concurrency read/write, payment and chat checks. On failure, stop load escalation, preserve evidence and address the cause.
4. Increase load gradually to the planned mixed scenario, retaining latency, errors, CPU/memory, task counts and scaling activities from matching time windows.
5. Coordinate fixed-capacity and autoscaling comparisons with YU, using the same dataset and workload. A permitted range of 1–4 does not prove successful scale-out to four tasks.
6. Stop the generator and confirm recovery and scale-in. Submit results and limitations. Recommend parameter changes only from measurements; YU applies them before a repeat comparison.

Code blocker: image-order fix PR #64 is merged. Backend and other required checks passed; PR CD was skipped as expected. This does not establish that the new main revision is deployed to staging; verify deployment records before testing.
