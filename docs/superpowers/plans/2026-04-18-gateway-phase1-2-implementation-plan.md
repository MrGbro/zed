# Gateway Phase1-2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现一个基于 JDK 21 的微内核网关，完成 Phase1-2 的可运行交付（HTTP/1.1+2、SPI 插件化、Radix 路由、Nacos 配置与服务发现、独立 admin 管理面）。

**Architecture:** 采用 `gateway-core + gateway-bootstrap` 的内核/编排分离模式，所有可替换能力通过 API 模块抽象并由实现模块适配。数据面采用异步 Filter Pipeline，控制面通过 admin 将动态配置发布至配置中心，节点通过快照原子切换热更新。依赖方向由构建期规则强约束，避免实现反向污染 core。

**Tech Stack:** Java 21, Maven multi-module, Netty, Spring Boot (admin), Nacos SDK, JUnit 5, ArchUnit, Micrometer, OpenTelemetry API

---

## 0. File Structure (先锁定文件职责)

### 0.1 Root
- Modify: `pom.xml`
- Create: `README.md`

### 0.2 Core/Foundation
- Create: `gateway-common/pom.xml`
- Create: `gateway-common/src/main/java/io/homeey/gateway/common/error/GatewayError.java`
- Create: `gateway-common/src/main/java/io/homeey/gateway/common/error/ErrorCategory.java`
- Create: `gateway-common/src/main/java/io/homeey/gateway/common/context/Attributes.java`
- Create: `gateway-core/pom.xml`
- Create: `gateway-core/src/main/java/io/homeey/gateway/core/context/GatewayContext.java`
- Create: `gateway-core/src/main/java/io/homeey/gateway/core/runtime/GatewayRuntime.java`

### 0.3 API Aggregates
- Create: `gateway-transport/pom.xml`
- Create: `gateway-transport/gateway-transport-api/pom.xml`
- Create: `gateway-transport/gateway-transport-api/src/main/java/io/homeey/gateway/transport/api/TransportServer.java`
- Create: `gateway-plugin/pom.xml`
- Create: `gateway-plugin/gateway-plugin-api/pom.xml`
- Create: `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/GatewayFilter.java`
- Create: `gateway-config/pom.xml`
- Create: `gateway-config/gateway-config-api/pom.xml`
- Create: `gateway-config/gateway-config-api/src/main/java/io/homeey/gateway/config/api/ConfigProvider.java`
- Create: `gateway-registry/pom.xml`
- Create: `gateway-registry/gateway-registry-api/pom.xml`
- Create: `gateway-registry/gateway-registry-api/src/main/java/io/homeey/gateway/registry/api/ServiceDiscoveryProvider.java`

### 0.4 Implementations
- Create: `gateway-transport/gateway-transport-netty/pom.xml`
- Create: `gateway-transport/gateway-transport-netty/src/main/java/io/homeey/gateway/transport/netty/NettyTransportServer.java`
- Create: `gateway-config/gateway-config-nacos/pom.xml`
- Create: `gateway-config/gateway-config-nacos/src/main/java/io/homeey/gateway/config/nacos/NacosConfigProvider.java`
- Create: `gateway-registry/gateway-registry-nacos/pom.xml`
- Create: `gateway-registry/gateway-registry-nacos/src/main/java/io/homeey/gateway/registry/nacos/NacosServiceDiscoveryProvider.java`

### 0.5 Bootstrap / Admin / Dist
- Create: `gateway-bootstrap/pom.xml`
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/BootstrapApplication.java`
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/config/BootstrapConfig.java`
- Create: `gateway-admin/pom.xml`
- Create: `gateway-admin/src/main/java/io/homeey/gateway/admin/AdminApplication.java`
- Create: `gateway-admin/src/main/java/io/homeey/gateway/admin/controller/RouteController.java`
- Create: `gateway-admin/src/main/resources/static/index.html`
- Create: `gateway-dist/pom.xml`

### 0.6 Tests / Architecture Guard
- Create: `gateway-core/src/test/java/io/homeey/gateway/core/FilterChainTest.java`
- Create: `gateway-core/src/test/java/io/homeey/gateway/core/RadixRouteLocatorTest.java`
- Create: `gateway-bootstrap/src/test/java/io/homeey/gateway/bootstrap/BootstrapWiringTest.java`
- Create: `gateway-admin/src/test/java/io/homeey/gateway/admin/RouteControllerTest.java`
- Create: `architecture-tests/pom.xml`
- Create: `architecture-tests/src/test/java/io/homeey/gateway/arch/DependencyRulesTest.java`

---

### Task 1: 建立多模块骨架与依赖基线

**Files:**
- Modify: `pom.xml`
- Create: `README.md`
- Create: `gateway-common/pom.xml`
- Create: `gateway-core/pom.xml`
- Create: `gateway-bootstrap/pom.xml`
- Create: `gateway-admin/pom.xml`
- Create: `gateway-dist/pom.xml`
- Create: `gateway-transport/pom.xml`
- Create: `gateway-plugin/pom.xml`
- Create: `gateway-config/pom.xml`
- Create: `gateway-registry/pom.xml`
- Create: `architecture-tests/pom.xml`

- [x] **Step 1: 写失败测试（构建层）**

在根目录创建最小验证脚本（临时命令，不入库）：
```bash
mvn -q -DskipTests validate
```
预期：FAIL，提示子模块不存在。

- [x] **Step 2: 先改根 `pom.xml` 为 parent 聚合**

写入以下核心结构（完整替换）：
```xml
<packaging>pom</packaging>
<modules>
  <module>gateway-common</module>
  <module>gateway-core</module>
  <module>gateway-bootstrap</module>
  <module>gateway-admin</module>
  <module>gateway-dist</module>
  <module>gateway-transport</module>
  <module>gateway-plugin</module>
  <module>gateway-config</module>
  <module>gateway-registry</module>
  <module>architecture-tests</module>
</modules>
```

- [x] **Step 3: 创建各父/子模块最小 `pom.xml`**

所有子模块统一继承 parent，并最小声明 `artifactId` 与 `packaging`。

示例（`gateway-common/pom.xml`）：
```xml
<parent>
  <groupId>io.homeey</groupId>
  <artifactId>zed</artifactId>
  <version>1.0-SNAPSHOT</version>
</parent>
<artifactId>gateway-common</artifactId>
```

- [x] **Step 4: 验证构建通过**

Run: `mvn -q -DskipTests validate`
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add pom.xml README.md gateway-common gateway-core gateway-bootstrap gateway-admin gateway-dist gateway-transport gateway-plugin gateway-config gateway-registry architecture-tests
git commit -m "build: initialize gateway multi-module structure"
```

---

### Task 2: 定义 API 抽象与公共模型（不含实现）

**Files:**
- Create: `gateway-common/src/main/java/io/homeey/gateway/common/error/ErrorCategory.java`
- Create: `gateway-common/src/main/java/io/homeey/gateway/common/error/GatewayError.java`
- Create: `gateway-common/src/main/java/io/homeey/gateway/common/context/Attributes.java`
- Create: `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/GatewayFilter.java`
- Create: `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/GatewayFilterChain.java`
- Create: `gateway-config/gateway-config-api/src/main/java/io/homeey/gateway/config/api/ConfigProvider.java`
- Create: `gateway-registry/gateway-registry-api/src/main/java/io/homeey/gateway/registry/api/ServiceDiscoveryProvider.java`
- Create: `gateway-transport/gateway-transport-api/src/main/java/io/homeey/gateway/transport/api/TransportServer.java`
- Create: `gateway-core/src/main/java/io/homeey/gateway/core/context/GatewayContext.java`

- [x] **Step 1: 写失败测试（类型可见性）**

Create `gateway-core/src/test/java/io/homeey/gateway/core/ApiContractCompileTest.java`:
```java
package io.homeey.gateway.core;

import io.homeey.gateway.plugin.api.GatewayFilter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiContractCompileTest {
  @Test
  void shouldLoadFilterType() {
    assertNotNull(GatewayFilter.class);
  }
}
```

- [x] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl gateway-core -Dtest=ApiContractCompileTest test`
Expected: FAIL，找不到 `GatewayFilter`。

- [x] **Step 3: 写 API 与 common 最小实现**

关键代码示例：
```java
public interface GatewayFilter {
  CompletionStage<Void> filter(GatewayContext context, GatewayFilterChain chain);
}
```
```java
public interface TransportServer {
  CompletionStage<Void> start();
  CompletionStage<Void> stop();
}
```

- [x] **Step 4: 运行测试确认通过**

Run: `mvn -q -pl gateway-core -Dtest=ApiContractCompileTest test`
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add gateway-common gateway-plugin/gateway-plugin-api gateway-config/gateway-config-api gateway-registry/gateway-registry-api gateway-transport/gateway-transport-api gateway-core
git commit -m "feat(api): add core contracts and common error/context models"
```

---

### Task 3: 实现核心 FilterChain 执行器与错误语义

**Files:**
- Create: `gateway-core/src/main/java/io/homeey/gateway/core/filter/DefaultGatewayFilterChain.java`
- Create: `gateway-core/src/main/java/io/homeey/gateway/core/filter/FilterExecutionPlan.java`
- Test: `gateway-core/src/test/java/io/homeey/gateway/core/FilterChainTest.java`

- [x] **Step 1: 写失败测试（顺序、短路、异常映射）**

```java
@Test
void shouldExecuteFiltersInOrder() { /* assert pre->routing->post 顺序 */ }

@Test
void shouldStopWhenFailCloseFilterThrows() { /* assert 短路并返回 GatewayError */ }
```

- [x] **Step 2: 运行失败测试**

Run: `mvn -q -pl gateway-core -Dtest=FilterChainTest test`
Expected: FAIL（类不存在）。

- [x] **Step 3: 写最小实现使测试通过**

核心结构：
```java
public final class DefaultGatewayFilterChain implements GatewayFilterChain {
  private final List<GatewayFilter> filters;
  private final int index;
  public CompletionStage<Void> next(GatewayContext ctx) { /* index+1 递进 */ }
}
```

- [x] **Step 4: 重跑测试**

Run: `mvn -q -pl gateway-core -Dtest=FilterChainTest test`
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add gateway-core/src/main/java/io/homeey/gateway/core/filter gateway-core/src/test/java/io/homeey/gateway/core/FilterChainTest.java
git commit -m "feat(core): implement async filter chain with fail semantics"
```

---

### Task 4: 实现 Radix 路由引擎与不可变快照

**Files:**
- Create: `gateway-core/src/main/java/io/homeey/gateway/core/route/RouteDefinition.java`
- Create: `gateway-core/src/main/java/io/homeey/gateway/core/route/RadixRouteLocator.java`
- Create: `gateway-core/src/main/java/io/homeey/gateway/core/route/RouteTableSnapshot.java`
- Test: `gateway-core/src/test/java/io/homeey/gateway/core/RadixRouteLocatorTest.java`

- [x] **Step 1: 写失败测试（host/path/method/header 匹配）**

```java
@Test
void shouldMatchByHostThenPathThenMethod() { }

@Test
void shouldReturnNotFoundWhenNoRouteMatches() { }
```

- [x] **Step 2: 执行失败测试**

Run: `mvn -q -pl gateway-core -Dtest=RadixRouteLocatorTest test`
Expected: FAIL。

- [x] **Step 3: 实现 Radix 核心与快照替换**

```java
public final class RouteTableSnapshot {
  private final String version;
  private final Map<String, RadixNode> hostBuckets;
}
```

- [x] **Step 4: 执行测试通过**

Run: `mvn -q -pl gateway-core -Dtest=RadixRouteLocatorTest test`
Expected: PASS。

- [x] **Step 5: Commit**

```bash
git add gateway-core/src/main/java/io/homeey/gateway/core/route gateway-core/src/test/java/io/homeey/gateway/core/RadixRouteLocatorTest.java
git commit -m "feat(core): add radix route locator with immutable snapshots"
```

---

### Task 5: 实现 Netty 传输层（Phase1 HTTP 代理）

**Files:**
- Create: `gateway-transport/gateway-transport-netty/src/main/java/io/homeey/gateway/transport/netty/NettyTransportServer.java`
- Create: `gateway-transport/gateway-transport-netty/src/main/java/io/homeey/gateway/transport/netty/NettyRequestAdapter.java`
- Create: `gateway-transport/gateway-transport-netty/src/main/java/io/homeey/gateway/transport/netty/NettyResponseAdapter.java`
- Test: `gateway-transport/gateway-transport-netty/src/test/java/io/homeey/gateway/transport/netty/NettyTransportServerTest.java`

- [x] **Step 1: 写失败测试（server start/stop + basic proxy）**
- [x] **Step 2: 运行失败测试**

Run: `mvn -q -pl gateway-transport/gateway-transport-netty -Dtest=NettyTransportServerTest test`
Expected: FAIL。

- [x] **Step 3: 最小实现 Netty TransportServer**

要求：
1. 支持 HTTP/1.1；
2. 暴露 start/stop；
3. 将请求适配为 `GatewayRequest` 并回写响应。

- [x] **Step 4: 重跑测试**

Expected: PASS。

- [x] **Step 5: Commit**

```bash
git add gateway-transport/gateway-transport-netty
git commit -m "feat(transport-netty): add minimal http transport server"
```

---

### Task 6: 实现 gateway-bootstrap 配置驱动装配

**Files:**
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/config/BootstrapConfig.java`
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/BootstrapApplication.java`
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/wiring/RuntimeFactory.java`
- Test: `gateway-bootstrap/src/test/java/io/homeey/gateway/bootstrap/BootstrapWiringTest.java`

- [x] **Step 1: 写失败测试（按配置装配 netty+nacos）**
- [x] **Step 2: 运行失败测试**

Run: `mvn -q -pl gateway-bootstrap -Dtest=BootstrapWiringTest test`
Expected: FAIL。

- [x] **Step 3: 实现 RuntimeFactory 与生命周期编排**

要求：
1. 读取本地配置；
2. 选择实现并装配；
3. 执行 `init -> start -> stop`。

- [x] **Step 4: 重跑测试**

Expected: PASS。

- [x] **Step 5: Commit**

```bash
git add gateway-bootstrap
git commit -m "feat(bootstrap): add config-driven runtime wiring"
```

---

### Task 7: 实现 Nacos 配置与服务发现适配（Phase2）

**Files:**
- Create: `gateway-config/gateway-config-nacos/src/main/java/io/homeey/gateway/config/nacos/NacosConfigProvider.java`
- Create: `gateway-registry/gateway-registry-nacos/src/main/java/io/homeey/gateway/registry/nacos/NacosServiceDiscoveryProvider.java`
- Test: `gateway-config/gateway-config-nacos/src/test/java/io/homeey/gateway/config/nacos/NacosConfigProviderTest.java`
- Test: `gateway-registry/gateway-registry-nacos/src/test/java/io/homeey/gateway/registry/nacos/NacosServiceDiscoveryProviderTest.java`

- [x] **Step 1: 写失败测试（订阅回调、快照更新）**
- [x] **Step 2: 运行失败测试**

Run:
```bash
mvn -q -pl gateway-config/gateway-config-nacos -Dtest=NacosConfigProviderTest test
mvn -q -pl gateway-registry/gateway-registry-nacos -Dtest=NacosServiceDiscoveryProviderTest test
```
Expected: FAIL。

- [x] **Step 3: 实现 API 适配层**

要求：
1. 封装 Nacos SDK 到 `ConfigProvider` / `ServiceDiscoveryProvider`；
2. 事件转换为统一模型；
3. 出错不破坏当前运行快照。

- [x] **Step 4: 重跑测试**

Expected: PASS。

- [x] **Step 5: Commit**

```bash
git add gateway-config/gateway-config-nacos gateway-registry/gateway-registry-nacos
git commit -m "feat(nacos): add config and discovery providers"
```

---

### Task 8: 实现 Admin OpenAPI + 简易控制台

**Files:**
- Create: `gateway-admin/src/main/java/io/homeey/gateway/admin/AdminApplication.java`
- Create: `gateway-admin/src/main/java/io/homeey/gateway/admin/controller/RouteController.java`
- Create: `gateway-admin/src/main/java/io/homeey/gateway/admin/controller/PluginController.java`
- Create: `gateway-admin/src/main/java/io/homeey/gateway/admin/service/PublishService.java`
- Create: `gateway-admin/src/main/resources/static/index.html`
- Test: `gateway-admin/src/test/java/io/homeey/gateway/admin/RouteControllerTest.java`

- [x] **Step 1: 写失败测试（路由 CRUD + 发布接口）**
- [x] **Step 2: 运行失败测试**

Run: `mvn -q -pl gateway-admin -Dtest=RouteControllerTest test`
Expected: FAIL。

- [x] **Step 3: 实现 Admin API 与发布服务**

要求：
1. 路由与插件绑定 CRUD；
2. 发布版本写入配置中心；
3. 返回发布记录。

- [x] **Step 4: 实现简易 Web 控制台**

最小功能：
1. 路由列表；
2. 创建/编辑；
3. 发布按钮；
4. 发布记录展示。

- [x] **Step 5: 重跑测试**

Expected: PASS。

- [x] **Step 6: Commit**

```bash
git add gateway-admin
git commit -m "feat(admin): add route/plugin management api and basic web console"
```

---

### Task 9: 热更新链路打通（配置发布 -> 节点原子切换）

**Files:**
- Modify: `gateway-core/src/main/java/io/homeey/gateway/core/runtime/GatewayRuntime.java`
- Create: `gateway-core/src/main/java/io/homeey/gateway/core/runtime/RuntimeSnapshotManager.java`
- Test: `gateway-core/src/test/java/io/homeey/gateway/core/RuntimeSnapshotManagerTest.java`

- [x] **Step 1: 写失败测试（发布事件触发快照替换）**
- [x] **Step 2: 运行失败测试**

Run: `mvn -q -pl gateway-core -Dtest=RuntimeSnapshotManagerTest test`
Expected: FAIL。

- [x] **Step 3: 实现订阅回调 + 后台构建 + 原子替换**

要求：
1. 构建失败时回退旧快照；
2. 打点 `config_update_success/fail` 指标。

- [x] **Step 4: 重跑测试**

Expected: PASS。

- [x] **Step 5: Commit**

```bash
git add gateway-core
git commit -m "feat(core): add runtime snapshot manager for hot reload"
```

---

### Task 10: 架构依赖规则门禁（防止反向依赖）

**Files:**
- Create: `architecture-tests/src/test/java/io/homeey/gateway/arch/DependencyRulesTest.java`
- Modify: `architecture-tests/pom.xml`

- [x] **Step 1: 写失败测试（插件依赖 core）**

```java
@ArchTest
static final ArchRule plugins_should_not_depend_on_core =
  noClasses().that().resideInAnyPackage("..plugin..")
    .should().dependOnClassesThat().resideInAnyPackage("..core..");
```

- [x] **Step 2: 运行失败测试（先故意验证）**

Run: `mvn -q -pl architecture-tests test`
Expected: FAIL（在引入临时违规依赖时）。

- [x] **Step 3: 移除临时违规并固化规则**

包括：
1. api 不依赖实现；
2. core 不暴露 reactor/netty 类型。

- [x] **Step 4: 运行架构测试通过**

Expected: PASS。

- [x] **Step 5: Commit**

```bash
git add architecture-tests
git commit -m "test(arch): enforce dependency direction and api purity"
```

---

### Task 11: 可观测性与错误码基线

**Files:**
- Create: `gateway-core/src/main/java/io/homeey/gateway/core/metrics/GatewayMetrics.java`
- Create: `gateway-core/src/main/java/io/homeey/gateway/core/tracing/TraceContextFactory.java`
- Create: `gateway-core/src/main/java/io/homeey/gateway/core/error/ErrorMapper.java`
- Test: `gateway-core/src/test/java/io/homeey/gateway/core/ErrorMapperTest.java`

- [x] **Step 1: 写失败测试（错误映射与 trace 注入）**
- [x] **Step 2: 运行失败测试**

Run: `mvn -q -pl gateway-core -Dtest=ErrorMapperTest test`
Expected: FAIL。

- [x] **Step 3: 实现 metrics/tracing/error mapper**

要求：
1. 错误码覆盖 `GW4xxx/GW5xxx`；
2. 指标覆盖 qps/p99/4xx/5xx/timeout/retry；
3. 响应头透传 traceId。

- [x] **Step 4: 重跑测试**

Expected: PASS。

- [x] **Step 5: Commit**

```bash
git add gateway-core
git commit -m "feat(obs): add metrics tracing and standardized error mapping"
```

---

### Task 12: E2E 联调与验收脚本

**Files:**
- Create: `scripts/e2e/run-phase2-smoke.ps1`
- Create: `scripts/perf/wrk-phase1.lua`
- Create: `scripts/perf/wrk-phase2.lua`
- Create: `docs/superpowers/checklists/phase1-phase2-acceptance.md`

- [x] **Step 1: 写失败验收脚本（先执行预期失败）**

Run: `powershell -File scripts/e2e/run-phase2-smoke.ps1`
Expected: FAIL（脚本或目标未就绪）。

- [x] **Step 2: 实现脚本并补齐文档**

脚本要求：
1. 启动 admin 与 node；
2. 调用 admin 创建路由并发布；
3. 校验 node 热更新生效；
4. 触发基础压测并输出结果。

- [x] **Step 3: 运行验收脚本通过**

Expected: PASS，输出关键指标。

- [x] **Step 4: Commit**

```bash
git add scripts docs/superpowers/checklists
git commit -m "chore(acceptance): add e2e smoke and perf baseline scripts"
```

---

## Plan Self-Review

### 1. Spec coverage

1. 模块拆分与父子包结构：Task 1/2 覆盖。
2. core 与 bootstrap 分离：Task 6 覆盖。
3. API 抽象不暴露 Reactor：Task 2 + Task 10 覆盖。
4. Filter 链与插件机制：Task 3 + Task 8 覆盖。
5. Radix 路由：Task 4 覆盖。
6. Nacos 配置与发现：Task 7 覆盖。
7. Admin OpenAPI + 简单控制台：Task 8 覆盖。
8. 热更新原子切换：Task 9 覆盖。
9. 异常/观测基线：Task 11 覆盖。
10. Phase1-2 验收：Task 12 覆盖。

### 2. Placeholder scan

已检查，无 `TODO/TBD/implement later` 占位语句。

### 3. Type consistency

1. 统一使用 `CompletionStage` 作为异步返回。
2. `GatewayFilter/GatewayFilterChain/GatewayContext` 在任务中命名一致。
3. `ConfigProvider/ServiceDiscoveryProvider/TransportServer` 命名一致。

---

Plan complete and saved to `docs/superpowers/plans/2026-04-18-gateway-phase1-2-implementation-plan.md`. Two execution options:

1. Subagent-Driven (recommended) - I dispatch a fresh subagent per task, review between tasks, fast iteration
2. Inline Execution - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?

