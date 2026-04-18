## 一、 整体架构设计：微内核 + 异步流水线
架构的核心思想是：**“内核抽象协议，插件实现逻辑”**。

### 1. 核心模块划分
+ **Gateway-Core (微内核)**：负责生命周期管理（Lifecycle）、配置加载、线程模型调度、Context 传递。
+ **Gateway-SPI (扩展标准)**：定义所有插件的接口协议。
+ **Gateway-Transport (传输层)**：抽象底层通信逻辑，屏蔽 Netty / Vert.x 差异。
+ **Gateway-Plugin (插件集)**：包含所有的 Filter 实现（限流、鉴权等）。
+ **Gateway-Common**：零依赖的工具类与基础定义。

### 2. 请求处理主流程
1. **Ingress**：Transport 层接收字节流，通过 Protocol 解码器转换为内部 `GatewayRequest`。
2. **Route Match**：路由引擎根据 Host、Path、Header 匹配目标 Backend。
3. **Filter Chain (Pipeline)**：
    - **Pre-Filter**：鉴权、流量整形、日志记录。
    - **Routing-Filter**：负载均衡、选址。
    - **Post-Filter**：响应体转换、Metrics 统计。
4. **Egress**：通过连接池将请求转发至后端。

### 3. Filter 机制设计
+ **全异步链式调用**：采用类似 Reactor 的 `Mono/Flux` 模式或 `CompletableFuture` 链，确保不阻塞 Netty IO 线程。
+ **上下文传递 (GatewayContext)**：封装 Request/Response，利用 `Attributes` 在 Filter 间传递元数据。
+ **自动激活与排序**：支持 `@Activate(group="pre", order=100)` 自动发现与加载。

### 4. SPI 机制改进（对比 Dubbo）
+ **按需加载**：支持延迟加载，避免启动时初始化所有插件。
+ **依赖注入**：支持简单的插件间依赖注入（类似 Dubbo 的 IOC）。
+ **条件装配**：支持根据配置环境变量动态决定是否启用某个实现类。

---

## 二、 功能蓝图：以能力域为核心
### 1. 流量接入层
+ **协议转换**：支持 HTTP1.1/2、gRPC 接入，并具备转换为下游协议的能力。
+ **零拷贝支持**：在转发过程中，尽可能利用 Netty 的 `CompositeByteBuf` 避免内存拷贝。
+ **安全加固**：内置抗常见的慢连接攻击（Slowloris）与 Header 注入防护。

### 2. 路由与服务发现
+ **高性能路由树**：采用 **Radix Tree（基数树）** 进行路径匹配，在大规模路由规则下保持 $O(k)$ 时间复杂度（$k$ 为路径长度）。
+ **动态感知**：插件化对接 Nacos/etcd，支持实例变动时的热更新，无需重启 Filter 链。

### 3. Filter 核心能力
+ **流量染色**：在 Filter 中为请求打标，支持后续的灰度路由。
+ **弹性限流**：支持分布式令牌桶（Redis 配合）与 本地自适应限流（根据 CPU/Load 自动扩缩）。
+ **响应体改写**：支持在流式返回过程中对 JSON/XML 进行动态转换。

### 4. 高可用与可观测性
+ **自愈能力**：集成断路器，支持基于错误率和慢调用比例的熔断。
+ **全链路追踪**：内置 OpenTelemetry 探针，记录 SpanId/TraceId 并在响应头返回。
+ **实时指标**：暴露 `/metrics` 端点，上报 P99 延迟、QPS、4xx/5xx 占比。

---

## 三、 分阶段演进路线
### Phase 1：骨架搭建 (MVP - Minimum Viable Product)
+ 完成 Maven 多模块定义。
+ 实现基于 Netty 的简易 HTTP 代理（支持转发请求到静态 IP）。
+ 硬编码实现简单的 `FilterChain` 接口。
+ 目标：**验证单机转发性能，跑通 IO 密集型压测。**

### Phase 2：内核增强 (Internal Extensibility)
+ 引入 **SPI 机制**，将所有 Filter 改为插件化加载。
+ 实现 **Radix Tree 路由引擎**。
+ 集成 **Nacos/Consul** 插件，支持基于服务名的转发。
+ 目标：**具备基本的微服务网关扩展能力。**

### Phase 3：工业级增强 (Production Ready)
+ 完善 **弹性能力**（限流、熔断、重试、超时控制）。
+ 支持 **多协议接入**（gRPC、WebSocket）。
+ 实现 **动态配置中心**（通过配置中心下发 Filter 规则并实时生效）。
+ 目标：**具备在测试/预发环境灰度运行的能力。**

### Phase 4：极致性能与生态 (Advanced & Ecosystem)
+ **多传输层抽象**：适配 Reactor Netty 和 Vert.x。
+ **性能调优**：实现全链路零拷贝，优化 GC 表现（减少对象分配）。
+ **控制台**：提供图形化界面管理路由、统计流量快照。
+ 目标：**打造高性能、可商用的开源中间件。**

---

## 四、 设计权衡 (Trade-offs)
### 1. 为什么不直接扩展 Spring Cloud Gateway (SCG)？
+ **技术栈绑定**：SCG 深度绑定 Spring 生态，对于追求轻量化、微内核、甚至想脱离 Spring 容器运行的场景不友好。
+ **性能瓶颈**：SCG 在某些极端场景下由于 Spring 的 AOP 和 Context 处理逻辑，性能损耗高于原生 Netty 构建的系统。
+ **控制力**：自研能更精细地控制内存池（ByteBuf）、线程模型和连接池策略。

### 2. 微内核 vs 单体网关
+ **微内核优在隔离**：核心代码库极小，Bug 率低。插件故障不至于拖垮整个内核的生命周期管理。
+ **劣在开发成本**：SPI 机制增加了系统的抽象层级，对于初学者来说，排查插件加载问题的门槛较高。

### 3. 多传输层抽象的复杂度是否值得？
+ **结论**：**值得，但应作为高级阶段目标。**
+ **理由**：Netty 是目前的工业标准，但 Reactor Netty 和 Vert.x 提供了更高级的异步编程模型。抽象传输层能让网关在未来轻松迁移到 Project Loom（虚拟线程）驱动的 IO 模型上，保持长期的技术先进性。

### 4. Filter 过多带来的性能问题如何解决？
+ **编译优化**：对于某些高频 Filter，可以考虑在启动阶段进行“链条拍平”，减少虚函数调用。
+ **上下文缓存**：避免在每个 Filter 中重复解析 Header 或 JSON，通过 `GatewayContext` 缓存中间结果。
+ **选择性激活**：通过路由元数据决定哪些 Filter 需要执行，避免无意义的“全量空跑”。

