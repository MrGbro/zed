# Traffic Governance V2 Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the old monolithic local governance parser/executor with Governance V2 plugin architecture (`api + engine + ability-local modules + state-local`) aligned to `docs/governance-2.md`.

**Architecture:** Keep governance SPI contracts in `gateway-governance-api`, move orchestration to `gateway-governance-engine`, and move capability implementations to dedicated `*-local` modules discovered by SPI. Execution order is fixed: rate-limit -> circuit precheck -> retry(timeout(upstream)) -> circuit record -> degrade mapping.

**Tech Stack:** Java 21, Maven multi-module, custom SPI loader (`ExtensionLoader`), JUnit 5, CompletionStage async model.

---

### Task 1: Restructure Governance Modules

**Files:**
- Modify: `gateway-governance/pom.xml`
- Modify: `gateway-bootstrap/pom.xml`
- Create: `gateway-governance/gateway-governance-engine/pom.xml`
- Create: `gateway-governance/gateway-governance-state-local/pom.xml`
- Create: `gateway-governance/gateway-governance-ratelimit-local/pom.xml`
- Create: `gateway-governance/gateway-governance-circuitbreaker-local/pom.xml`
- Create: `gateway-governance/gateway-governance-retry-local/pom.xml`
- Create: `gateway-governance/gateway-governance-timeout-local/pom.xml`
- Create: `gateway-governance/gateway-governance-degrade-local/pom.xml`
- Delete: `gateway-governance/gateway-governance-local/pom.xml` (and old module sources/resources/tests)

- [ ] **Step 1: Add new submodules to governance aggregator and remove old local module**

```xml
<modules>
  <module>gateway-governance-api</module>
  <module>gateway-governance-engine</module>
  <module>gateway-governance-state-local</module>
  <module>gateway-governance-ratelimit-local</module>
  <module>gateway-governance-circuitbreaker-local</module>
  <module>gateway-governance-retry-local</module>
  <module>gateway-governance-timeout-local</module>
  <module>gateway-governance-degrade-local</module>
</modules>
```

- [ ] **Step 2: Create each module `pom.xml` with dependency on `gateway-governance-api`**

```xml
<dependency>
  <groupId>io.homeey</groupId>
  <artifactId>gateway-governance-api</artifactId>
  <version>${project.version}</version>
</dependency>
```

- [ ] **Step 3: Update bootstrap dependencies to engine + all local ability modules**

```xml
<dependency><groupId>io.homeey</groupId><artifactId>gateway-governance-engine</artifactId><version>${project.version}</version></dependency>
<dependency><groupId>io.homeey</groupId><artifactId>gateway-governance-state-local</artifactId><version>${project.version}</version></dependency>
```

- [ ] **Step 4: Build compile check**

Run: `mvn -q -pl gateway-governance -am -DskipTests compile`  
Expected: compile success for all new governance modules

### Task 2: Introduce Governance V2 API/SPI Contracts

**Files:**
- Modify: `gateway-governance/gateway-governance-api/src/main/java/io/homeey/gateway/governance/api/*.java`
- Create: `gateway-governance/gateway-governance-api/src/main/java/io/homeey/gateway/governance/api/{FailureMode,GovernanceFailureKind,GovernanceEngine,GovernancePolicy,PolicyFactory,PolicyHandler,GovernanceStateStore,GovernanceScheduler,FailureModeResolver}.java`
- Create: `gateway-governance/gateway-governance-api/src/main/resources/META-INF/gateway/*` (new SPI resource definitions)
- Delete: old V1 API (`GovernanceExecutor`, `GovernancePolicyParser`, V1 aggregated `GovernancePolicy`, and noop V1 classes)

- [ ] **Step 1: Write failing API compatibility test for new policy defaults and parsing expectations**

```java
@Test
void shouldDefaultRetryStatusesAndFailureMode() {
  RetryPolicy p = RetryPolicy.disabled();
  assertEquals(FailureMode.FAIL_OPEN, p.failureMode());
  assertTrue(p.retryOnStatuses().contains(503));
}
```

- [ ] **Step 2: Add new policy records implementing `GovernancePolicy` and include `failureMode`**

```java
public interface GovernancePolicy {
    String ability();
    boolean enabled();
    FailureMode failureMode();
}
```

- [ ] **Step 3: Define SPI interfaces in api with `@SPI` defaults**

```java
@SPI("local")
public interface FailureModeResolver {
    FailureMode resolve(String ability, FailureMode configured);
}
```

- [ ] **Step 4: Run governance-api tests**

Run: `mvn -q -pl gateway-governance/gateway-governance-api -am test`  
Expected: all api tests pass

### Task 3: Implement Engine + Filter Orchestration

**Files:**
- Create: `gateway-governance/gateway-governance-engine/src/main/java/io/homeey/gateway/governance/engine/{DefaultGovernanceEngine,DefaultTrafficGovernanceFilter,GovernancePlan,GovernanceExecutionContext}.java`
- Create: `gateway-governance/gateway-governance-engine/src/main/resources/META-INF/gateway/{io.homeey.gateway.governance.api.GovernanceEngine,io.homeey.gateway.plugin.api.GatewayFilter}`
- Create: `gateway-governance/gateway-governance-engine/src/test/java/io/homeey/gateway/governance/engine/{DefaultGovernanceEngineTest,DefaultTrafficGovernanceFilterTest}.java`

- [ ] **Step 1: Write failing tests for fixed execution order and bypass behavior**

```java
assertEquals(List.of("ratelimit.pre", "circuit.pre", "retry.around", "timeout.around", "circuit.record"), trace);
```

- [ ] **Step 2: Implement engine orchestration with failure-kind mapping and degrade hook**

```java
// pre-check -> around -> record -> degrade fallback
```

- [ ] **Step 3: Implement governance filter activation (`governance.enabled=true`) and route policy extraction**

```java
@Activate(group = {"route"}, order = -60, conditions = {"governance.enabled=true"})
public final class DefaultTrafficGovernanceFilter extends TrafficGovernanceGatewayFilter { ... }
```

- [ ] **Step 4: Run engine module tests**

Run: `mvn -q -pl gateway-governance/gateway-governance-engine -am test`  
Expected: engine/filter tests pass

### Task 4: Implement State Local SPI

**Files:**
- Create: `gateway-governance/gateway-governance-state-local/src/main/java/io/homeey/gateway/governance/state/local/{LocalGovernanceStateStore,LocalGovernanceScheduler,LocalFailureModeResolver}.java`
- Create: `gateway-governance/gateway-governance-state-local/src/main/resources/META-INF/gateway/{io.homeey.gateway.governance.api.GovernanceStateStore,io.homeey.gateway.governance.api.GovernanceScheduler,io.homeey.gateway.governance.api.FailureModeResolver}`
- Create: `gateway-governance/gateway-governance-state-local/src/test/java/io/homeey/gateway/governance/state/local/LocalGovernanceSchedulerTest.java`

- [ ] **Step 1: Write failing scheduler timeout/delay tests**
- [ ] **Step 2: Implement local state store and scheduled executor based scheduler**
- [ ] **Step 3: Implement resolver default map (`ratelimit/circuit=close`, `retry/timeout/degrade=open`)**
- [ ] **Step 4: Run state-local tests**

Run: `mvn -q -pl gateway-governance/gateway-governance-state-local -am test`  
Expected: all state-local tests pass

### Task 5: Implement Ability Local Modules (Factory + Handler + Tests)

**Files:**
- Create/Modify module packages under:
  - `gateway-governance-ratelimit-local`
  - `gateway-governance-circuitbreaker-local`
  - `gateway-governance-retry-local`
  - `gateway-governance-timeout-local`
  - `gateway-governance-degrade-local`

- [ ] **Step 1: RateLimit module TDD (factory parse + token bucket pre-check)**
- [ ] **Step 2: CircuitBreaker module TDD (state transitions + precheck/record)**
- [ ] **Step 3: Retry module TDD (retryOnStatuses/retryOnTimeout/backoff)**
- [ ] **Step 4: Timeout module TDD (attempt timeout using scheduler)**
- [ ] **Step 5: Degrade module TDD (triggerOn mapping + response build)**
- [ ] **Step 6: Register each module SPI via `META-INF/gateway/io.homeey.gateway.governance.api.PolicyFactory` and `...PolicyHandler`**

Run: `mvn -q -pl gateway-governance -am test`  
Expected: all governance modules tests pass

### Task 6: Integrate, Remove V1, and Verify End-to-End

**Files:**
- Delete: old V1 local implementation files and resource mappings under `gateway-governance/gateway-governance-local/**`
- Modify: `gateway-bootstrap/src/test/java/io/homeey/gateway/bootstrap/wiring/DefaultGatewayRequestHandlerTest.java`
- Create: governance integration tests in bootstrap if missing

- [ ] **Step 1: Add/adjust bootstrap integration tests for governance V2 keys**
- [ ] **Step 2: Remove old V1 module directory and references**
- [ ] **Step 3: Run targeted verification**

Run: `mvn -q -pl gateway-bootstrap -am -Dtest=DefaultGatewayRequestHandlerTest -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test`  
Expected: bootstrap tests pass with governance V2

- [ ] **Step 4: Run full verification**

Run: `mvn -q -DskipTests validate`  
Expected: validate success

Run: `mvn test`  
Expected: all tests pass

