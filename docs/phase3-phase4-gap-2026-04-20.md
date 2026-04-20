# Phase3-Phase4 功能 Gap 评估（2026-04-20）

## 1. 评估说明

本文对当前仓库在 Phase3（Production Ready）和 Phase4（Advanced & Ecosystem）的落地情况做代码级盘点，输出：
1. 已完成项
2. 关键缺口（Gap）
3. 重点演进方向与优先级

评估依据：
- `docs/arch-desc.md`
- `docs/governance-observability-phase-plan-2026-04-19.md`
- 当前主干代码（截止 2026-04-20）

---

## 2. 总体结论

- Phase3 完成度：**约 60%（部分完成）**
- Phase4 完成度：**约 30%（起步）**

当前已具备“可运行 + 可扩展 + 基础治理 + 基础可观测”能力；
距离“生产治理闭环”和“生态化高性能”还有明显差距。

---

## 3. Phase3 评估（Production Ready）

### 3.1 治理内核（限流/熔断/重试/超时/降级）
- 状态：已完成（内核）
- 完成度：85%
- 证据：
  - `gateway-governance/pom.xml`
  - `gateway-governance/gateway-governance-engine/src/main/java/io/homeey/gateway/governance/engine/DefaultGovernanceEngine.java`

新增进展：
- 限流 provider 化已落地（`local/sentinel`）
  - `gateway-governance/gateway-governance-api/src/main/java/io/homeey/gateway/governance/api/RateLimitPolicy.java`
  - `gateway-governance/gateway-governance-ratelimit-sentinel/src/main/java/io/homeey/gateway/governance/ratelimit/sentinel/SentinelRateLimitPolicyHandler.java`

主要 Gap：
1. 分布式状态与跨节点一致性能力不足（当前偏本地实现）
2. 策略中心仍是 key-value 级别，缺少版本化和编排语义

### 3.2 可观测（Metrics/Trace/AccessLog）
- 状态：部分完成
- 完成度：70%
- 证据：
  - `gateway-observe/pom.xml`
  - `gateway-observe/gateway-observe-otel/src/main/java/io/homeey/gateway/observe/otel/OtelObserveProvider.java`
  - `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/wiring/DefaultGatewayRequestHandler.java`

主要 Gap：
1. 指标维度仍偏最小集
2. 采样策略与高基数治理不足
3. 缺少生产级 SLO 验收基线文档

### 3.3 治理流程化（发布状态机/审批/回滚）
- 状态：部分完成
- 完成度：65%
- 证据：
  - `gateway-admin/src/main/java/io/homeey/gateway/admin/model/ReleaseState.java`
  - `gateway-admin/src/main/java/io/homeey/gateway/admin/service/ReleaseGovernanceService.java`

主要 Gap：
1. canary 目前主要停留在控制面记录，未完整进入数据面路由决策
2. 自动回滚仍依赖人工触发，缺少基于 SLO 的自动执行
3. 审批流缺少多角色权限与审计标准化

### 3.4 多协议接入（gRPC/WebSocket）
- 状态：部分完成
- 完成度：35%
- 证据：
  - Reactor 传输层已支持 HTTP/1.1 + H2C：
    `gateway-transport/gateway-transport-reactor/src/main/java/io/homeey/gateway/transport/reactor/ReactorTransportServer.java`

主要 Gap：
1. gRPC 代理语义未完整落地（路由/metadata/状态码映射）
2. WebSocket 升级与长连接治理未完整落地

---

## 4. Phase4 评估（Advanced & Ecosystem）

### 4.1 多传输层抽象
- 状态：已完成（基础）
- 完成度：75%
- 证据：
  - netty/vertx/reactor 三套 `TransportServerFactory` SPI 注册均已落地

主要 Gap：
1. 多实现之间缺少统一能力基准与一致性验收

### 4.2 性能体系（零拷贝/GC/压测门禁）
- 状态：未开始（体系化）
- 完成度：20%

主要 Gap：
1. 缺少固定 benchmark 场景与回归阈值
2. 缺少“优化前后对比 + 产物沉淀”流程

### 4.3 生态与平台化（控制台/插件生态）
- 状态：部分完成
- 完成度：35%
- 证据：
  - 管理台基础页面与 CRUD 已有：`gateway-admin/src/main/resources/static/index.html`

主要 Gap：
1. 缺少完整治理控制台（审批流、灰度进度、回滚看板、告警联动）
2. 缺少插件目录、版本治理、兼容矩阵等平台能力

---

## 5. 关键 Gap 清单（优先级）

| 优先级 | Gap 项 | 当前状态 | 影响 |
|---|---|---|---|
| P0 | Canary 打通控制面到数据面（真实灰度） | 部分完成 | 直接影响发布安全 |
| P0 | 自动回滚（基于 SLO） | 未开始 | 无法自动止损 |
| P0 | gRPC / WebSocket 数据面能力 | 部分完成 | 协议覆盖不足 |
| P1 | 策略中心模型化（版本/作用域/窗口） | 部分完成 | 难规模治理 |
| P1 | 观测生产化（采样/高基数/统一口径） | 部分完成 | 定位效率受限 |
| P1 | 性能门禁（固定压测+阈值） | 未开始 | 性能回归不可控 |
| P2 | 平台化控制台与插件生态 | 部分完成 | 长期运营成本高 |

---

## 6. 重点演进方向（建议）

### 方向 A（重点 / P0）：发布治理闭环
目标：形成“可灰度、可自动回滚、可审计”的发布体系。

建议动作：
1. 定义 canary 规则并下发到数据面
2. 数据面按 header/tag/权重执行灰度
3. 接入 SLO 阈值触发自动回滚

### 方向 B（重点 / P0）：协议能力补齐
目标：补齐 gRPC/WebSocket 的可用接入和治理对齐。

建议动作：
1. gRPC 代理链路（metadata/状态码/超时）
2. WebSocket 升级与长连接生命周期治理
3. 纳入现有治理与观测链路

### 方向 C（重点 / P1）：策略中心与观测生产化
目标：将“功能可用”提升到“生产可运营”。

建议动作：
1. 策略模型化（版本、作用域、回滚点）
2. 统一指标与日志字段规范
3. 建立性能回归门禁和验收模板

---

## 7. 建议排期

### 近期（2-4 周）
1. 完成方向 A 最小闭环（canary 数据面 + 自动回滚）
2. 完成 gRPC 最小可用接入

### 中期（4-8 周）
1. 完成 WebSocket 与策略中心模型化
2. 建立性能门禁与阶段验收模板
3. 补齐治理控制台可视化能力

---

## 8. 风险提示

1. 先扩协议、后补发布闭环，会放大生产风险
2. 没有性能门禁，Phase4 优化结果不可持续
3. 控制面持续快于数据面，会形成“可配置但难生效”的长期错位
