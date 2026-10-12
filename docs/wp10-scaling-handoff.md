# WP10 给 YU 的扩缩容参数交接

状态：建议参数与仪表盘配置已准备；尚未部署 AWS，也未发送给 YU。默认值不是云端压测得出的最优值。

| 服务 | 指标 | 目标 | 最小 / 最大任务数 |
|---|---|---|---|
| Core | ECS 平均 CPU | 60% | 1 / 4 |
| Search | ALB 每目标每分钟请求数 | 1000 | 1 / 4 |
| Chat | ECS 平均 CPU | 60% | 1 / 4 |

扩容冷却 60 秒，缩容冷却 300 秒。保留现有 Terraform 默认值。Search 使用请求量而不是会议简写的 CPU 60%，需要 YU 确认；1000 是暂定起点。Chat 长连接数和消息吞吐不会被 ALB HTTP 请求量完整反映，暂保留 CPU 策略。

生产默认启用；staging 默认不启用，演示扩缩容需显式 enable_autoscaling=true。1→4 是容量边界，不是 CPU 超阈值后立即跳到 4。检查配额、镜像健康和任务启动时间。

## 部署和验收

YU 在正确 AWS 账号及 Terraform workspace 执行 plan，检查仅预期 dashboard 资源及已有基础设施变更，再按团队流程 apply。输出 wp10_dashboard_name 定位仪表盘。该资源可能产生 CloudWatch 费用，不在本地验证时部署。

仪表盘覆盖各服务 CPU/内存、期望/运行任务数、ALB p50/p95、每目标请求量和目标 5xx。Container Insights 已在集群配置中启用。缺失曲线不能当作零；确认目标组维度、服务有流量和指标权限。ALB 延迟单位为秒，不能直接当成 k6 毫秒；WebSocket 消息延迟取 k6 指标。

在 staging 先做低流量 smoke，再逐级加压，记录任务数变化、CPU、p95、错误率、扩缩容时间。关注数据库/连接池与搜索瓶颈，不能只凭 CPU 低就继续增加并发。压测结束停止发生器，确认缩容恢复并记录费用/容量。

## 待补证据

- AWS dashboard URL 与截图、实际部署 workspace。
- 相同负载下扩容前后 p95/错误率、任务数时间线。
- Search 1000 请求/目标/分钟阈值的实测依据。
- 旧单 Core 基线只能作为历史对照，不能证明拆服务后的性能或 AWS 扩缩容。

指标参考：https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-cloudwatch-metrics.html 、https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Container-Insights-metrics-ECS.html
