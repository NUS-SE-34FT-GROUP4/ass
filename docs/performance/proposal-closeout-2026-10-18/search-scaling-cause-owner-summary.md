# Production Search scaling cause — owner-supplied evidence

Supplied by the user on 19 October 2026 as YU's Markdown export summary of `aws application-autoscaling describe-scaling-activities` for `service/c2csectrade-production/search` and `aws cloudwatch describe-alarm-history`. This is a translated record summary, not independently retrieved raw AWS JSON. Source SHA-256: cdef3c0b1e3b2502ce08b9c4704b3bf95832e3932545111b6cb9cca1446025d0.

Policy: `c2csectrade-production-search-requests`, target tracking on ALBRequestCountPerTarget, target 1,000 requests per target per minute, minimum 1 and maximum 4.

All times below are Singapore time on 18 October 2026 (UTC+8).

| Time | Record reported by YU |
|---|---|
| 14:07:18 | High alarm OK → ALARM; three consecutive one-minute Sum datapoints 1086, 1664 and 1748 exceeded 1000 |
| 14:07:18–14:08:21 | Successful desired-count change 1 → 2; cause identifies the high alarm triggering the named policy |
| 14:10:18 | High alarm ALARM → OK; datapoints 1708, 1225 and 0 |
| 14:24:08 | Low alarm OK → ALARM; fifteen consecutive one-minute datapoints were zero, below 900 |
| 14:24:09–14:24:40 | Successful desired-count change 2 → 1; cause identifies the low alarm triggering the named policy |

The intervals match the directly observed ECS events and task-count screenshots. This closes the policy-cause gap for the bounded Search run. The export summary also reports no Core or Chat scaling activities on that date; this is owner-reported and is not a continuous task-count measurement. No 1 → 4 scaling, 200-VU capacity, linear scaling or fixed-capacity comparison is established. Alarm evaluation periods are distinct from configured cooldown durations.
