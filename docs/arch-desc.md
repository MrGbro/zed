# 架构设计

## 顶层模块设计

```text
gateway
├── gateway-dependencies              # BOM（版本统一管理）

├── gateway-common                    # 通用基础（无业务语义）
│   ├── util
│   ├── concurrent
│   ├── config
│   ├── exception
│   └── lifecycle

├── gateway-api                       # 对外抽象（极薄）
│   ├── request
│   ├── response
│   ├── invocation
│   └── metadata

├── gateway-core                      # ⭐微内核（只编排）
│   ├── bootstrap
│   ├── pipeline                      # Filter链构建
│   ├── dispatcher                    # 请求分发
│   ├── context                       # 调用上下文
│   ├── registry                      # SPI注册/缓存
│   └── lifecycle

├── gateway-faulttolerance-engine     # ⭐执行引擎（唯一组合层）
│   ├── chain                         # 执行链（组合模式）
│   ├── executor                      # 执行调度
│   ├── context                       # 容错上下文
│   └── policy                        # 顺序/策略编排（不实现具体能力）

# ================= SPI 原子能力体系 =================

├── gateway-spi-extension             # ⭐SPI基础设施（唯一根依赖）

├── gateway-spi-filter
├── gateway-spi-routing
├── gateway-spi-loadbalance

├── gateway-spi-ratelimit
├── gateway-spi-circuitbreaker
├── gateway-spi-retry
├── gateway-spi-timeout
├── gateway-spi-fallback

├── gateway-spi-protocol
├── gateway-spi-serialization
├── gateway-spi-transport

├── gateway-spi-discovery
├── gateway-spi-config

├── gateway-spi-metrics
├── gateway-spi-tracing
├── gateway-spi-logging

# ================= Plugin 实现层（严格对齐 SPI） =================

├── gateway-plugin-filter
├── gateway-plugin-routing
├── gateway-plugin-loadbalance

├── gateway-plugin-ratelimit
│   ├── tokenbucket
│   ├── leakybucket

├── gateway-plugin-circuitbreaker
│   ├── slidingwindow
│   ├── halfopen

├── gateway-plugin-retry
│   ├── fixed
│   ├── exponential

├── gateway-plugin-timeout
│   ├── default

├── gateway-plugin-fallback
│   ├── default

├── gateway-plugin-protocol
│   ├── protocol-http
│   ├── protocol-grpc

├── gateway-plugin-serialization
│   ├── json
│   ├── protobuf

├── gateway-plugin-transport
│   ├── netty
│   ├── vertx

├── gateway-plugin-discovery
│   ├── nacos
│   ├── consul

├── gateway-plugin-config
│   ├── local
│   ├── remote

├── gateway-plugin-metrics
│   ├── prometheus

├── gateway-plugin-tracing
│   ├── opentelemetry

├── gateway-plugin-logging
│   ├── default

# ================= 外部接入层 =================

├── gateway-adapter
│   ├── adapter-netty
│   ├── adapter-http
│   ├── adapter-springboot
│   ├── adapter-k8s

# ================= 工程支持 =================

├── gateway-test
│   ├── benchmark
│   ├── integration-test

├── gateway-distribution
│   ├── bin
│   ├── conf
│   └── bootstrap

└── pom.xml
```

## 依赖关系

```text
common → api → spi-extension → 各SPI子模块
                                 ↓
                           core / engine
                                 ↓
                              plugin
                                 ↓
                              adapter
                                 ↓
                           distribution
```

## 硬性约束

### SPI 规则

- 每个 SPI 模块 = 一个原子能力
- SPI 模块之间 ❌ 禁止依赖
- SPI 只依赖：
  common + api + spi-extension

### Plugin 规则

- 一个 plugin 只实现一个 SPI
- plugin ❌ 不能依赖 plugin
- plugin ❌ 不能依赖 core / engine
- plugin ✔ 只依赖对应 SPI + common + api

### Core 规则

- 只负责流程编排（pipeline / dispatcher）
- ❌ 不实现任何策略
- ❌ 不出现具体 plugin 类

### Engine（最关键）

- 这是唯一“能力组合层”
- 负责：
  RateLimit → CircuitBreaker → Timeout → Retry → Fallback
- ❌ 不实现具体算法
- ✔ 只调用 SPI

### Adapter 规则

- 只负责 IO（Netty / HTTP）
- 只调用 core
- ❌ 不接触 SPI
- ❌ 不写业务逻辑
## 调用链
```text
Adapter (Netty/HTTP)
        ↓
Core Dispatcher
        ↓
Filter Chain
        ↓
FaultTolerance Engine
        ↓
  RateLimit SPI
        ↓
  CircuitBreaker SPI
        ↓
  Timeout SPI
        ↓
  Retry SPI
        ↓
Invoker
        ↓
Fallback SPI
        ↓
Response
```