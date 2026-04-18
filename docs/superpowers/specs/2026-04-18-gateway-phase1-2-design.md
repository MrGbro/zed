# 网关工程化设计方案（Phase1-2）

- 文档日期：2026-04-18
- 文档范围：Phase1-2 详细设计 + Phase3-4 后续规划
- 基线目标：单机 `20k-50k QPS`，`P99 < 20ms`
- 首发实现：服务发现与配置中心均基于 Nacos（同时定义可扩展 API）

## 1. 背景与目标

本方案面向“微内核 + 异步流水线 + 插件化扩展”网关，重点解决以下问题：

1. 用最小核心实现高性能数据面转发。
2. 用清晰 API 契约隔离核心能力与实现细节，避免技术栈绑定。
3. 在 Phase1-2 交付可用能力的同时，为 Phase3-4 演进（gRPC、多传输层、生产治理）提前预留路径。

## 2. 范围定义

### 2.1 本次落地（Phase1-2）

1. 数据面：HTTP/1.1 + HTTP/2。
2. 架构：微内核、插件链、Radix 路由引擎、配置驱动启动编排。
3. 控制面：独立 `gateway-admin`，提供 Admin OpenAPI + 简单 Web 控制台。
4. 配置体系：
- 本地静态配置承载启动参数；
- 路由与插件策略由管理面维护并发布到配置中心；
- 网关节点订阅并热更新。
5. 集成：Nacos 服务发现 + Nacos 配置中心。

### 2.2 明确不在本次落地

1. gRPC 数据面接入（仅做接口预留与兼容设计）。
2. 完整治理控制台（审批流、多租户、细粒度 RBAC、回滚编排）。
3. 全套生态化插件市场。

## 3. 核心原则与架构护栏

1. API 抽象优先：`core` 与上层禁止暴露 Reactor/Netty/Vert.x 专有 API。
2. 插件隔离：插件实现模块禁止依赖 `gateway-core`，只能依赖 API 层与 `gateway-common`。
3. 核心纯净：`gateway-core` 只做核心能力，不承担装配编排。
4. 启动编排分离：新增 `gateway-bootstrap`，负责配置驱动装配与生命周期驱动。
5. 可替换实现：transport/config/registry 均通过 API 抽象对接，首发 Nacos，后续平滑扩展。

## 4. 模块设计

## 4.1 顶层模块

1. `gateway-common`
2. `gateway-core`
3. `gateway-bootstrap`
4. `gateway-admin`
5. `gateway-dist`

## 4.2 聚合父模块与子模块

1. `gateway-transport`（聚合）
- `gateway-transport-api`
- `gateway-transport-netty`（Phase1-2）
- `gateway-transport-reactor-netty`（后续）
- `gateway-transport-vertx`（后续）

2. `gateway-plugin`（聚合）
- `gateway-plugin-api`
- `gateway-plugin-auth`
- `gateway-plugin-ratelimit`
- `gateway-plugin-observability`
- `gateway-plugin-traffic-tag`
- `gateway-plugin-rewrite`

3. `gateway-config`（聚合）
- `gateway-config-api`
- `gateway-config-nacos`（Phase2）
- `gateway-config-consul`（后续）
- `gateway-config-etcd`（后续）
- `gateway-config-zk`（后续）

4. `gateway-registry`（聚合）
- `gateway-registry-api`
- `gateway-registry-nacos`（Phase2）
- `gateway-registry-consul`（后续）
- `gateway-registry-etcd`（后续）
- `gateway-registry-zk`（后续）

## 4.3 依赖白名单（强约束）

1. `gateway-core -> gateway-common + all *-api`
2. `gateway-bootstrap -> gateway-core + gateway-common + all *-api + 选中的实现模块`
3. `gateway-plugin-* -> gateway-plugin-api + gateway-common (+ 必要第三方SDK)`
4. `gateway-config-* -> gateway-config-api + gateway-common`
5. `gateway-registry-* -> gateway-registry-api + gateway-common`
6. `gateway-transport-* -> gateway-transport-api + gateway-common`
7. `gateway-admin -> gateway-common + gateway-config-api (+ admin 自身依赖)`

## 4.4 禁止项

1. 任意实现模块依赖 `gateway-core`（除 `gateway-bootstrap`）。
2. 任意 API 模块依赖实现模块。
3. 在 `core/api` 暴露 Reactor/Netty/Vert.x 类型。
4. 插件直接操作底层连接对象。

## 5. 数据面处理链路

1. Ingress：`TransportServer` 接收请求并转为 `GatewayRequest`。
2. Route Match：`RouteLocator` 依据 Host/Path/Method/Header 选路由。
3. Filter Pipeline：按 `Pre -> Routing -> Post` 执行。
4. Egress：通过连接池向上游发起请求并写回客户端。
5. Observability：记录 Metrics/Trace/Structured Logs。

### 5.1 Phase1 与 Phase2 差异

1. Phase1：静态路由 + 硬编码 FilterChain + 固定后端。
2. Phase2：路由、插件、配置、服务发现全部插件化并支持热更新。

## 6. 核心接口与异步抽象

接口层统一使用 `CompletionStage`，不暴露响应式 API。

```java
public interface GatewayFilter {
    CompletionStage<Void> filter(GatewayContext ctx, GatewayFilterChain chain);
}

public interface GatewayFilterChain {
    CompletionStage<Void> next(GatewayContext ctx);
}

public interface RouteLocator {
    CompletionStage<RouteMatch> locate(GatewayRequest request);
}

public interface ServiceDiscoveryProvider {
    CompletionStage<List<ServiceInstance>> getInstances(String serviceName);
    void subscribe(String serviceName, InstanceChangeListener listener);
}

public interface ConfigProvider {
    CompletionStage<ConfigSnapshot> get(String dataId, String group);
    void subscribe(String dataId, String group, ConfigChangeListener listener);
}

public interface TransportServer {
    CompletionStage<Void> start();
    CompletionStage<Void> stop();
}
```

### 6.1 `GatewayContext` 约束

1. 包含 `request`、`response`、`routeMatch`、`traceContext`、`attributes`。
2. `attributes` 用于 Filter 间元数据传递与中间结果缓存。
3. 业务链路不透传底层 channel 对象。

## 7. 启动编排（gateway-bootstrap）

`gateway-bootstrap` 负责配置驱动装配：

1. 加载本地静态配置（端口、线程模型、Nacos 地址、实现类型）。
2. 根据配置选择实现（transport/config/registry/plugin）。
3. 构建 `GatewayRuntime` 并执行 `init -> start -> stop` 生命周期。
4. 注册优雅停机钩子，控制连接排空与资源释放。

## 8. 控制面与配置体系

## 8.1 控制面职责

1. `gateway-admin` 负责路由/插件配置管理与发布。
2. 提供 Admin OpenAPI + 简单 Web 控制台。
3. 通过配置中心 API 发布版本化配置。

## 8.2 配置分层

1. 本地静态配置：启动参数与节点级参数。
2. 动态业务配置：路由、插件绑定、策略参数，由 admin 管理并发布。

## 8.3 关键数据模型

1. `RouteDefinition`：路由条件、优先级、上游引用、启停状态。
2. `PluginBinding`：全局或路由维度绑定、执行顺序、插件配置。
3. `PolicySet`：限流/熔断/重试等策略集合。
4. `PublishRecord`：版本、变更摘要、操作者、发布时间、状态。

## 8.4 发布与生效流程

1. 管理端编辑草稿并校验。
2. 发布新版本到配置中心。
3. 网关节点订阅事件并拉取新快照。
4. 后台构建新运行快照，成功后原子切换。
5. 失败保留旧快照并上报告警。

## 9. 路由引擎与服务发现

## 9.1 Radix 路由引擎

1. 按 Host 分桶，桶内使用 Radix Tree 做 Path 匹配。
2. Path 命中后再做 Method/Header 精筛。
3. 路由数据采用不可变快照，更新时原子替换引用。

## 9.2 服务发现抽象与实现

1. 定义统一 `ServiceDiscoveryProvider` 接口。
2. 首发实现 `gateway-registry-nacos`。
3. 后续扩展 `consul/etcd/zk` 时不改 core/filter 契约。

## 10. Filter 插件体系

## 10.1 分类

1. `PreFilter`：鉴权、限流、流量标记。
2. `RoutingFilter`：实例选择与转发。
3. `PostFilter`：响应改写、指标与追踪补充。
4. `ErrorFilter`：异常归一化与错误输出。

## 10.2 激活与排序

1. 支持 `@Activate(group, order, condition)` 元信息。
2. 支持全局激活、路由激活、条件激活。
3. 执行计划在发布或变更时预编译为不可变结构。

## 10.3 依赖与生命周期

1. 插件构造器注入，仅允许 API/common 依赖。
2. 生命周期 `init -> start -> stop`。
3. 插件执行语义支持 `FAIL_CLOSE` 与 `FAIL_OPEN`。

## 10.4 性能优化

1. 链条拍平，减少深层调用。
2. 上下文缓存，避免重复解析。
3. 按路由元数据选择性执行插件。

## 11. 异常、超时、重试、熔断与回压

## 11.1 错误模型

1. 分类：`CLIENT_ERROR`、`UPSTREAM_ERROR`、`SYSTEM_ERROR`、`CONTROL_PLANE_ERROR`。
2. 统一对象：`GatewayError{code, category, httpStatus, retryable, message, causeId}`。

## 11.2 超时预算

1. 总预算：`requestTotalTimeout`。
2. 子预算：`connect/read/write/plugin`。
3. 预算耗尽即时失败，避免长尾拖垮。

## 11.3 重试策略（Phase2）

1. 仅对可重试错误 + 幂等请求默认启用。
2. 支持 `maxAttempts + backoff+jitter + retryBudget`。
3. 非幂等写请求默认不自动重试。

## 11.4 熔断与隔离

1. 维度：`route + upstream cluster`。
2. 状态机：`CLOSED -> OPEN -> HALF_OPEN -> CLOSED`。
3. 并发与 pending 上限保护，防止级联故障。

## 11.5 回压策略

1. 全局与路由级并发阈值。
2. 短队列与快速失败。
3. 过载优先返回 `429`，上游不可用返回 `503`。

## 12. 测试与验收

## 12.1 测试分层

1. 单元测试：core 路由、链路、错误映射、预算逻辑。
2. 契约测试：所有 API 实现一致性。
3. 集成测试：bootstrap + netty + nacos 端到端。
4. 压测：静态路由、动态配置、异常注入、热更新抖动。

## 12.2 Phase1 验收

1. HTTP/1.1 静态转发可用。
2. 基础 Filter 链打通。
3. 达到 `20k+ QPS`，`P99 < 20ms`（基准场景）。

## 12.3 Phase2 验收

1. Radix 路由可用。
2. 插件化加载、排序、条件激活可用。
3. Nacos 配置与发现热更新可用。
4. Admin OpenAPI + 简单 Web 控制台可用。
5. 常态场景维持 `20k-50k QPS` 档位（按场景分档验收）。

## 12.4 CI 门禁

1. 单测与契约测试必须通过。
2. 架构依赖方向校验（Enforcer/ArchUnit）必须通过。
3. 最小性能回归低于阈值则阻断合并。

## 13. 里程碑与交付节奏

1. `M1`：Phase1 完成（静态路由 + 基础链路 + 性能基线）。
2. `M2`：Phase2 完成（SPI 插件化 + Nacos + Admin 基础治理）。
3. `M3`：进入 Phase3 生产增强。
4. `M4`：进入 Phase4 生态化与多传输实现。

## 14. Phase3-4 后续计划（纳入路线图）

## 14.1 Phase3（Production Ready）

1. 增强治理：审批流、灰度发布、自动回滚。
2. 增强韧性：限流/熔断/重试/超时策略完善与统一策略中心。
3. 协议扩展：引入 gRPC 接入实现（基于现有抽象不破坏核心契约）。
4. 稳定性工程：故障注入与混沌测试常态化。

## 14.2 Phase4（Advanced & Ecosystem）

1. 多传输实现：`reactor-netty`、`vertx` 完整适配。
2. 极致性能：全链路零拷贝、对象分配压缩、GC 优化。
3. 控制台增强：多租户、细粒度 RBAC、变更可视化与回滚编排。
4. 生态能力：插件兼容矩阵、签名校验、模板与市场化。

## 15. 风险与缓解

1. 抽象过度风险：Phase1 仅实现最小必要接口，避免早期过度设计。
2. 动态配置一致性风险：采用快照构建 + 原子替换 + 回滚机制。
3. 性能回退风险：设立固定压测基线与 CI 性能门禁。
4. 插件质量风险：契约测试 + 故障注入 + 失败语义强约束。

## 16. 实施结论

本设计在 Phase1-2 内可交付“可运行、可扩展、可治理”的网关基础形态，同时通过 API 边界、启动编排分层与阶段里程碑，为 Phase3-4 的 gRPC、多传输层与生态化演进保留了稳定路径。
