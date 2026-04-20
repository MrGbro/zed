# Traffic Governance V2 重构设计（方案B）

- Date: 2026-04-20
- Status: Approved in-session（待实现）
- Scope: 服务治理数据面重构（RateLimit / CircuitBreaker / Retry / Timeout / Degrade）
- Decision:
  - 采用方案B（引擎 + 能力插件化）
  - 接受不兼容变更
  - 分能力 `failureMode` 可配置（`fail-open` / `fail-close`）

## 1. 目标与边界

### 1.1 目标

1. 以 `docs/governance-2.md` 为基线，重建清晰治理分层：
   `TrafficGovernanceFilter -> GovernanceEngine -> Policies -> StateStore/Scheduler`。
2. 将治理能力拆成可独立扩展的 SPI，实现“能力实现可插拔、执行编排可复用”。
3. 首版完整覆盖五类能力：限流、熔断、重试、超时、降级。
4. 统一能力级故障策略：每个能力独立配置 `failureMode`。

### 1.2 非目标

1. 首版不引入分布式状态存储（仅提供本地实现与抽象接口）。
2. 首版不实现复杂自适应算法（如动态阈值、机器学习限流）。
3. 首版不保证旧配置键与旧 SPI 兼容（明确不兼容升级）。

## 2. 模块重构

`gateway-governance` 聚合模块调整为：

1. `gateway-governance-api`
2. `gateway-governance-engine`
3. `gateway-governance-state-local`
4. `gateway-governance-ratelimit-local`
5. `gateway-governance-circuitbreaker-local`
6. `gateway-governance-retry-local`
7. `gateway-governance-timeout-local`
8. `gateway-governance-degrade-local`

职责划分：

1. `api`：治理策略模型、执行上下文、SPI 契约、通用结果类型。
2. `engine`：过滤器与执行编排，按固定顺序驱动策略处理器。
3. `*-local`：每类能力的默认本地实现与 SPI 注册。
4. `state-local`：治理状态与调度器默认本地实现。

## 3. SPI 定义归属

所有 SPI 统一定义在 `gateway-governance-api`：

1. `PolicyFactory`
2. `PolicyHandler<TPolicy>`
3. `GovernanceStateStore`
4. `GovernanceScheduler`
5. `FailureModeResolver`

### 3.1 契约原则

1. `engine` 依赖 SPI，不依赖任意具体本地实现类。
2. 能力模块只依赖 `api`，通过 `META-INF/gateway/*` 注册。
3. 新能力或新存储实现可独立新增模块接入，不修改 `engine` 主流程。

## 4. 执行链路设计

### 4.1 请求执行顺序（固定）

1. `TrafficGovernanceFilter` 读取 `route.policySet`，由各 `PolicyFactory` 解析生成策略对象。
2. `GovernanceEngine` 组装 `GovernanceExecutionContext`（routeId/request/attributes/policies）。
3. 前置准入：
   - `RateLimitPolicyHandler`（拒绝则短路）
   - `CircuitBreakerPolicyHandler.preCheck`（开路则短路）
4. 环绕执行：
   - `RetryPolicyHandler`（最外层）
   - `TimeoutPolicyHandler`（包裹单次 attempt）
   - 最内层执行 `chain.next(context)`。
5. 结果回写：
   - `CircuitBreakerPolicyHandler.recordOutcome` 记录结果并驱动状态迁移。
6. 失败映射：
   - `DegradePolicyHandler` 根据失败类别决定是否降级响应。

### 4.2 失败类别

标准失败类别用于降级触发与可观测标记：

1. `rate_limited`
2. `circuit_open`
3. `timeout`
4. `retry_exhausted`
5. `governance_error`

## 5. 状态与调度设计

### 5.1 StateStore

所有可变状态通过 `GovernanceStateStore` 读写，不允许散落在 handler 私有静态结构中。

键规范（本地实现）：

1. `ratelimit:{routeId}:{key}`
2. `circuit:{routeId}`
3. `retry:{requestId}`（如需记录 attempt 上下文）

### 5.2 Scheduler

超时和重试退避统一通过 `GovernanceScheduler`：

1. `scheduleTimeout(duration, token)`
2. `scheduleDelay(backoff, token)`

实现要求：

1. 不使用 `Thread.sleep` 阻塞请求线程。
2. 不在 handler 内部自行创建临时线程处理定时任务。

## 6. 配置模型（V2）

采用扁平命名空间，配置入口仍为 `route.policySet.entries`。

### 6.1 全局键

1. `governance.enabled`
2. `governance.engine`（首版支持 `local`）

### 6.2 能力通用键

每个能力统一包含：

1. `governance.<ability>.enabled`
2. `governance.<ability>.failureMode`（`fail-open` / `fail-close`）

### 6.3 RateLimit

1. `governance.ratelimit.enabled`
2. `governance.ratelimit.failureMode`
3. `governance.ratelimit.qps`
4. `governance.ratelimit.burst`
5. `governance.ratelimit.keyType`（`route` / `ip` / `header`）
6. `governance.ratelimit.keyHeader`（`keyType=header` 生效）

### 6.4 CircuitBreaker

1. `governance.circuitbreaker.enabled`
2. `governance.circuitbreaker.failureMode`
3. `governance.circuitbreaker.failureRateThreshold`
4. `governance.circuitbreaker.minimumCalls`
5. `governance.circuitbreaker.openDurationMillis`
6. `governance.circuitbreaker.halfOpenMaxCalls`

### 6.5 Retry

1. `governance.retry.enabled`
2. `governance.retry.failureMode`
3. `governance.retry.maxAttempts`
4. `governance.retry.backoffMillis`
5. `governance.retry.retryOnStatuses`（CSV）
6. `governance.retry.retryOnTimeout`

### 6.6 Timeout

1. `governance.timeout.enabled`
2. `governance.timeout.failureMode`
3. `governance.timeout.durationMillis`

### 6.7 Degrade

1. `governance.degrade.enabled`
2. `governance.degrade.failureMode`
3. `governance.degrade.status`
4. `governance.degrade.contentType`
5. `governance.degrade.body`
6. `governance.degrade.triggerOn`（CSV：
   `rate_limited,circuit_open,timeout,retry_exhausted,governance_error`）

### 6.8 解析策略

1. `governance.enabled != true`：整链旁路。
2. 单能力配置非法：该能力视为未启用。
3. 解析阶段默认 fail-open，不因配置错误阻断请求。

## 7. FailureMode 语义

`FailureModeResolver` 按能力解析故障策略：

1. `fail-open`：记录旁路标记，继续流程。
2. `fail-close`：立即拒绝并进入降级映射。

默认建议：

1. `ratelimit`：`fail-close`
2. `circuitbreaker`：`fail-close`
3. `retry`：`fail-open`
4. `timeout`：`fail-open`
5. `degrade`：`fail-open`

## 8. 可观测性与上下文字段

通过 `GatewayContext.attributes()` 写入治理标记：

1. `governance.rate_limited`
2. `governance.circuit_open`
3. `governance.timeout`
4. `governance.retry.attempts`
5. `governance.degraded`
6. `governance.<ability>.bypassed`（能力级 fail-open 旁路）

## 9. 迁移策略

### 9.1 删除与替换

删除旧一体化实现：

1. `LocalGovernanceExecutor`
2. `LocalGovernancePolicyParser`
3. 旧聚合式治理模型及配套旧测试

替换为：

1. `TrafficGovernanceFilter + GovernanceEngine`
2. 能力化 `PolicyFactory/PolicyHandler` 插件实现

### 9.2 依赖调整

`gateway-bootstrap` 不再只依赖 `gateway-governance-local`，改为依赖：

1. `gateway-governance-engine`
2. `gateway-governance-state-local`
3. 各能力 local 模块

## 10. 测试策略

### 10.1 单元测试

1. `gateway-governance-engine`：
   - 执行顺序
   - 失败模式分流
   - 过滤器旁路逻辑
2. 各能力模块：
   - 限流桶行为
   - 熔断状态迁移
   - 重试触发条件
   - 超时触发条件
   - 降级映射规则

### 10.2 集成测试

`gateway-bootstrap` 补 `DefaultGatewayRequestHandler` 治理集成场景：

1. 无治理配置保持现行为。
2. 能力单独启用可独立生效。
3. 多能力组合策略顺序正确。
4. 不同 `failureMode` 行为符合预期。

### 10.3 验证命令

1. `mvn -q -DskipTests validate`
2. `mvn -q -pl gateway-governance -am test`
3. `mvn -q -pl gateway-bootstrap -am -Dtest=DefaultGatewayRequestHandlerTest -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test`
4. `mvn test`

## 11. 风险与控制

1. 风险：模块拆分后 SPI 装配错误导致能力未生效。
   - 控制：为每个 SPI 注册增加发现性测试与启动时自检日志。
2. 风险：不兼容配置导致旧路由治理失效。
   - 控制：在 admin/publish 路径增加配置校验提示（实现阶段纳入任务）。
3. 风险：调度器实现不当引入线程泄漏。
   - 控制：统一 `GovernanceScheduler` 生命周期管理并添加并发测试。

