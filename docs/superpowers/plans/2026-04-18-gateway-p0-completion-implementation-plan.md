# Gateway P0 Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 P0 缺口：真实转发链路、HTTP/2 入站、真实 Nacos + fallback、Admin 发布闭环、节点生命周期 init/start/stop。

**Architecture:** 保持现有多模块边界，补齐 API 契约并以配置驱动装配实现。`gateway-admin` 负责发布路由快照到 Nacos；`gateway-bootstrap` 负责装配 provider/proxy/transport 并驱动生命周期；`gateway-core` 负责运行时快照原子切换；`gateway-transport-netty` 负责 HTTP/1.1+2 入站与请求适配。

**Tech Stack:** Java 21, Maven multi-module, Netty, AsyncHttpClient, Reactor Netty HttpClient, OkHttp, Nacos Java SDK, Spring Boot, Jackson, JUnit 5

---

## 0. File Structure

### 0.1 API & Model
- Modify: `gateway-config/gateway-config-api/src/main/java/io/homeey/gateway/config/api/ConfigProvider.java`
- Modify: `gateway-registry/gateway-registry-api/src/main/java/io/homeey/gateway/registry/api/ServiceDiscoveryProvider.java`
- Create: `gateway-transport/gateway-transport-api/src/main/java/io/homeey/gateway/transport/api/HttpRequestMessage.java`
- Create: `gateway-transport/gateway-transport-api/src/main/java/io/homeey/gateway/transport/api/HttpResponseMessage.java`
- Create: `gateway-transport/gateway-transport-api/src/main/java/io/homeey/gateway/transport/api/GatewayRequestHandler.java`
- Create: `gateway-proxy/pom.xml`
- Create: `gateway-proxy/gateway-proxy-api/src/main/java/io/homeey/gateway/proxy/api/ProxyRequest.java`
- Create: `gateway-proxy/gateway-proxy-api/src/main/java/io/homeey/gateway/proxy/api/ProxyResponse.java`
- Create: `gateway-proxy/gateway-proxy-api/src/main/java/io/homeey/gateway/proxy/api/ProxyClient.java`

### 0.2 Nacos & Fallback
- Modify: `gateway-config/gateway-config-nacos/pom.xml`
- Modify: `gateway-registry/gateway-registry-nacos/pom.xml`
- Modify: `gateway-config/gateway-config-nacos/src/main/java/io/homeey/gateway/config/nacos/NacosConfigProvider.java`
- Modify: `gateway-registry/gateway-registry-nacos/src/main/java/io/homeey/gateway/registry/nacos/NacosServiceDiscoveryProvider.java`
- Create: `gateway-config/gateway-config-nacos/src/main/java/io/homeey/gateway/config/nacos/InMemoryConfigProvider.java`
- Create: `gateway-registry/gateway-registry-nacos/src/main/java/io/homeey/gateway/registry/nacos/InMemoryServiceDiscoveryProvider.java`

### 0.3 Data Plane & Proxy Clients
- Modify: `gateway-transport/gateway-transport-netty/pom.xml`
- Modify: `gateway-transport/gateway-transport-netty/src/main/java/io/homeey/gateway/transport/netty/NettyTransportServer.java`
- Create: `gateway-proxy/gateway-proxy-async-http/src/main/java/io/homeey/gateway/proxy/asynchttp/AsyncHttpProxyClient.java`
- Create: `gateway-proxy/gateway-proxy-reactor-netty/src/main/java/io/homeey/gateway/proxy/reactor/ReactorNettyProxyClient.java`
- Create: `gateway-proxy/gateway-proxy-okhttp/src/main/java/io/homeey/gateway/proxy/okhttp/OkHttpProxyClient.java`
- Modify: `gateway-transport/gateway-transport-netty/src/main/java/io/homeey/gateway/transport/netty/NettyRequestAdapter.java`
- Modify: `gateway-transport/gateway-transport-netty/src/main/java/io/homeey/gateway/transport/netty/NettyResponseAdapter.java`

### 0.4 Core Runtime & Route Snapshot
- Modify: `gateway-core/pom.xml`
- Modify: `gateway-core/src/main/java/io/homeey/gateway/core/route/RouteDefinition.java`
- Modify: `gateway-core/src/main/java/io/homeey/gateway/core/route/RouteTableSnapshot.java`
- Modify: `gateway-core/src/main/java/io/homeey/gateway/core/runtime/RuntimeSnapshotManager.java`
- Create: `gateway-core/src/main/java/io/homeey/gateway/core/runtime/RouteSnapshotCodec.java`

### 0.5 Bootstrap Wiring & Lifecycle
- Modify: `gateway-bootstrap/pom.xml`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/config/BootstrapConfig.java`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/wiring/RuntimeFactory.java`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/BootstrapApplication.java`
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/wiring/DefaultGatewayRequestHandler.java`
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/wiring/ProxyClientFactory.java`

### 0.6 Admin Publish Closure
- Modify: `gateway-admin/pom.xml`
- Modify: `gateway-admin/src/main/java/io/homeey/gateway/admin/service/PublishService.java`
- Modify: `gateway-admin/src/main/java/io/homeey/gateway/admin/controller/RouteController.java`
- Create: `gateway-admin/src/main/java/io/homeey/gateway/admin/config/AdminNacosConfig.java`

### 0.7 Docs
- Modify: `docs/phase1-phase2-completion-gap-2026-04-18.md`

---

### Task 1: 扩展 API 契约（Config publish + ProxyClient + RequestHandler）

- [ ] **Step 1: 先写/更新 API 编译测试（失败）**
- [ ] **Step 2: 调整 API 接口与模型最小实现**
- [ ] **Step 3: 运行 API 相关测试（通过）**

### Task 2: 落地 Nacos 真实集成与 fallback

- [ ] **Step 1: 先补 Nacos provider 行为测试（失败）**
- [ ] **Step 2: 接入 Nacos SDK，保留 fallback 自动回退**
- [ ] **Step 3: 运行 config/registry 测试（通过）**

### Task 3: 实现可切换 ProxyClient（默认 async-http-client）

- [ ] **Step 1: 为 async/reactor/okhttp 选择逻辑补测试（失败）**
- [ ] **Step 2: 实现 ProxyClientFactory + 三种客户端（reactor/okhttp 可为可用骨架）**
- [ ] **Step 3: 运行相关测试（通过）**

### Task 4: Netty 入站升级为真实转发并支持 HTTP/2

- [ ] **Step 1: 写失败测试（非 `/ping`，按 handler 回写，支持 keepalive）**
- [ ] **Step 2: 引入 request handler 驱动的真实数据路径 + h2c 升级 pipeline**
- [ ] **Step 3: 运行 transport 测试（通过）**

### Task 5: Core 热更新快照升级

- [ ] **Step 1: 写失败测试（路由快照 JSON 解码 + 原子替换）**
- [ ] **Step 2: 扩展 route 模型（upstream 字段）并实现 codec**
- [ ] **Step 3: 运行 core 测试（通过）**

### Task 6: Bootstrap 生命周期与装配闭环

- [ ] **Step 1: 写失败测试（init/start/stop 顺序与 graceful stop）**
- [ ] **Step 2: RuntimeFactory 装配 Nacos/fallback、ProxyClient、Transport、Handler**
- [ ] **Step 3: BootstrapApplication 明确 init/start/stop**
- [ ] **Step 4: 运行 bootstrap 测试（通过）**

### Task 7: Admin 发布接入 Nacos

- [ ] **Step 1: 写失败测试（publish 实际调用 ConfigProvider.publish）**
- [ ] **Step 2: PublishService 输出完整快照并写入 `gateway.routes.json@GATEWAY`**
- [ ] **Step 3: 运行 admin 测试（通过）**

### Task 8: 文档回填

- [ ] **Step 1: 更新完成/待完成清单，标记 P0 项状态**

---

## Plan Self-Review

1. Spec coverage:
- 真实转发链路：Task 3 + Task 4 + Task 6
- HTTP/2 入站：Task 4
- Nacos + fallback：Task 2 + Task 6 + Task 7
- Admin 发布热更新闭环：Task 5 + Task 6 + Task 7
- 生命周期 init/start/stop：Task 6

2. Placeholder scan:
- 计划中无 `TODO/TBD/implement later` 占位。

3. Type consistency:
- 统一使用 `CompletionStage` 异步契约。
- 统一 dataId/group：`gateway.routes.json` / `GATEWAY`。

---

Execution mode fixed by user instruction: **Inline Execution** (no sub-agent mode).
