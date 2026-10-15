# WP10 给 YU 的扩缩容参数交接

状态（2026-10-15 更新）：仪表盘已部署并有历史截图；YU 经 CUI 转达确认正在为 staging 开启扩缩容。apply 完成、数据重导和索引重建仍待通知；以下是确认的目标配置，不代表已读取云端实际状态，也不是实测最优值。

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


## 10/15 冻结前交接结论

来源：CUI 转达 YU/Chenglin 的确认。代码冻结日期为 **2026-10-16**。

- 三个服务容量均为 1–4；Core/Chat CPU 60%，Search 每目标每分钟请求数 1000；扩容冷却 60 秒、缩容冷却 300 秒。YU 正在 apply，完成后通知。
- 旧压测数据已清理。apply 后由 YU 重导 2,000 个账号和 10,000 个商品（ID 从 1,000,000 开始），并重建索引；不能将之前采样结果当作重导完成的证明。
- 准备实际运行时再由 YU 当场签发两小时 token，私下交付。凭证只留在忽略目录，不提交 token、密码或签名密钥。
- 文档、截图和测试证据 PR 在代码冻结后仍可合并。实测后的 Terraform 参数建议交给 YU 修改并 apply；不把配置变更混同于应用代码冻结。
- 10/16 起 production 的建立不影响继续测 staging；所有负载仍明确指向 staging。

### Redis 架构局限（共同确认，冻结前不改代码）

目前只有 Core 使用 Redis，Chat 和 Search 不连接 Redis。现有 Core key 没有统一服务前缀，
因此尚未满足 proposal 的按服务命名约定，但在当前服务使用范围内不存在跨服务 key 冲突。
这是当前架构的局限，不声称已实现命名空间隔离。未来其他服务接入时，再增加服务前缀
或使用独立逻辑库，并制定缓存、锁及过期键迁移方案。逻辑库划分不等同于安全权限隔离。

### 下一轮执行顺序

1. 等 YU 确认 apply、数据导入和索引重建完成；记录实际镜像、任务数、策略和部署版本。
2. 确认发生器和监控就绪，再请求新 fixtures/token；校验有效期及账号、商品、库存和余额样本。
3. 先执行低并发读写/支付/聊天检查；失败则停止升级负载、保留失败证据并处理原因。
4. 逐级增加负载至既定混合场景，保留同一时窗的延迟、错误率、CPU/内存、任务数和扩缩容活动。
5. 固定容量与扩缩容对照需和 YU 协调配置切换、保持相同数据及工作负载；不把“允许 1–4”当作已成功扩到 4。
6. 停止发生器后确认恢复与缩容；提交结果与局限。仅根据测量提出参数调整，由 YU apply 后复测。

代码阻塞项：图片排序修复 PR #64 已合并，后端及其他必跑检查通过，PR 的 CD 按流程跳过。
这不等于新的 main 已部署到 staging；运行前仍须核对部署记录。
