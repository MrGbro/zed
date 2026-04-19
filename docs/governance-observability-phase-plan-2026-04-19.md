# 服务治理与可观测性实施分析（2026-04-19）

## 1. 结论摘要

当前仓库在“服务治理”和“可观测性”方面处于 **Phase1-2 基线完成、生产级能力未完成** 状态。  
根据现有路线图，这两块能力应在 **Phase3（Production Ready）** 作为主目标落地。

## 2. 阶段定位（按现有规划）

### 2.1 路线图定位

规划文档已明确：

1. `M2` 对应 Phase2 完成（SPI 插件化 + Nacos + Admin 基础治理）。
2. `M3` 进入 Phase3（Production Ready）。
3. Phase3重点为：
   - 增强治理：审批流、灰度发布、自动回滚。
   - 增强韧性：限流、熔断、重试、超时策略完善与统一策略中心。

参考：

- `docs/superpowers/specs/2026-04-18-gateway-phase1-2-design.md`（第13、14章）

### 2.2 缺口文档定位

缺口评估文档也给出了当前状态：

1. 治理控制面能力不完整（P1）：
   - 控制面模型/API 仍是 baseline，不是完整治理域模型。
2. 真实可观测未集成（P1）：
   - 尚未接入 Micrometer/OpenTelemetry 与 `/metrics` 对外暴露体系。

参考：

- `docs/archive/phase1-phase2-completion-gap-2026-04-18.md`

## 3. 当前实现现状（代码证据）

### 3.1 可观测性现状

当前有“本地基线能力”，但未形成生产可观测闭环：

1. `gateway-core` 已有本地计数器模型：`GatewayMetrics`。
2. 已有 traceId 生成：`TraceContextFactory`。
3. 但未看到 exporter/endpoint 级对接（Micrometer registry、OTLP exporter、统一 metrics endpoint）。

参考：

- `gateway-core/src/main/java/io/homeey/gateway/core/metrics/GatewayMetrics.java`
- `gateway-core/src/main/java/io/homeey/gateway/core/tracing/TraceContextFactory.java`

### 3.2 服务治理现状

当前有“基础控制面”，但未到生产治理：

1. `gateway-admin` 已具备路由、插件绑定、发布记录基础接口。
2. 有 `PolicySet/PluginBinding/PublishRecord` 基础模型与发布链路。
3. 尚缺审批流、灰度策略、自动回滚编排、统一策略中心的完整实现。

参考：

- `gateway-admin/src/main/java/io/homeey/gateway/admin/controller/RouteController.java`
- `gateway-admin/src/main/java/io/homeey/gateway/admin/service/PublishService.java`
- `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/PolicySet.java`
- `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/PluginBinding.java`
- `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/PublishRecord.java`

## 4. 建议实施方案（与现仓库对齐）

## 4.1 Phase2.5：可观测先行补齐（建议 1-2 周）

目标：在不重构主流程的前提下，先形成最小生产可观测闭环。

工作包：

1. 引入 Micrometer 体系：
   - 把 `GatewayMetrics` 迁移/桥接为 `MeterRegistry` 指标。
   - 输出核心指标：`gateway_requests_total`、`gateway_request_latency`、`gateway_errors_total`、`gateway_upstream_timeout_total`。
2. 暴露 `/metrics`：
   - 在网关进程中提供可抓取端点（建议 Prometheus 格式）。
3. 引入 OpenTelemetry：
   - 透传/生成 traceId，补齐 span（ingress、route-match、proxy、egress）。
   - 支持 OTLP exporter。
4. 标准化访问日志：
   - 最少包含 `traceId`、`routeId`、`upstream`、`status`、`latencyMs`、`errorCategory`。

验收：

1. 本地抓取到 `/metrics` 指标。
2. Jaeger/Tempo 可看到端到端 trace。
3. 关键链路日志可按 traceId 关联。

## 4.2 Phase3-A：治理流程化（建议 2-3 周）

目标：将“发布动作”升级为“受控治理流程”。

工作包：

1. 发布状态机：
   - `DRAFT -> VALIDATED -> APPROVED -> PUBLISHED -> ROLLED_BACK`。
2. 审批流：
   - 引入发布审批人、审批意见、审批时间。
3. 灰度发布：
   - 按 header/tag/权重分流；支持按路由或策略集灰度。
4. 自动回滚：
   - 关联 SLO（错误率、延迟阈值）；触发自动回退到上个稳定版本。

验收：

1. 单次发布具备可审计轨迹。
2. 灰度策略可在线启停与回滚。
3. 自动回滚具备可观测告警记录。

## 4.3 Phase3-B：韧性策略中心（建议 2-3 周）

目标：让 `PolicySet` 从“配置数据”变为“运行时生效策略”。

工作包：

1. 统一策略中心：
   - 将限流/熔断/重试/超时统一建模并下发。
2. 数据面执行器：
   - 在 `gateway-bootstrap`/`gateway-core` 中实现策略执行链。
3. 插件模块化：
   - 新增 `gateway-plugin-ratelimit`、`gateway-plugin-resilience`、`gateway-plugin-observability`。
4. 热更新一致性：
   - 继续采用快照构建 + 原子切换 + 失败保留旧快照。

验收：

1. 各策略可按路由粒度独立生效。
2. 策略变更可热更新且不中断流量。
3. 压测下策略启用后性能回退在可控范围内。

## 5. 优先级与推进建议

建议优先顺序：

1. **先做可观测（Phase2.5）**：没有观测，治理策略上线风险不可控。
2. **再做治理流程（Phase3-A）**：先把发布行为纳入可追踪、可回滚的管控框架。
3. **最后做韧性策略中心（Phase3-B）**：在可观测 + 受控发布前提下逐步增强策略。

## 6. 里程碑定义（建议）

1. `M2.5`：可观测闭环完成（metrics + trace + structured logs）。
2. `M3-A`：治理流程完成（审批 + 灰度 + 回滚）。
3. `M3-B`：策略中心完成（限流/熔断/重试/超时统一生效）。

