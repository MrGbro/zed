# Traffic Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement data-plane governance runtime for rate limit, circuit breaker, degrade, timeout, and retry with config-driven no-op defaults.

**Architecture:** Add a route-level `TrafficGovernanceFilter` that reads policy keys from `PolicySet`, delegates to a local `PolicyEngine` backed by in-memory state store and scheduler, and applies fallback/degrade responses when enabled. Keep behavior unchanged when governance is not configured.

**Tech Stack:** Java 21, Maven multi-module, existing SPI/filter chain, JUnit 5.

---

## 0. File Structure

### 0.1 Governance API contracts
- Create: `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/governance/GovernancePolicy.java`
- Create: `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/governance/RateLimitPolicy.java`
- Create: `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/governance/CircuitBreakerPolicy.java`
- Create: `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/governance/TimeoutPolicy.java`
- Create: `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/governance/RetryPolicy.java`
- Create: `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/governance/DegradePolicy.java`

### 0.2 Data-plane governance runtime
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/governance/GovernancePolicyParser.java`
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/governance/PolicyEngine.java`
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/governance/PolicyDecision.java`
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/governance/LocalPolicyEngine.java`
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/governance/TrafficGovernanceFilter.java`
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/governance/state/GovernanceStateStore.java`
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/governance/state/InMemoryGovernanceStateStore.java`

### 0.3 Wiring and integration
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/wiring/DefaultGatewayRequestHandler.java`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/wiring/RuntimeFactory.java`

### 0.4 Tests
- Create: `gateway-bootstrap/src/test/java/io/homeey/gateway/bootstrap/governance/GovernancePolicyParserTest.java`
- Create: `gateway-bootstrap/src/test/java/io/homeey/gateway/bootstrap/governance/LocalPolicyEngineTest.java`
- Modify: `gateway-bootstrap/src/test/java/io/homeey/gateway/bootstrap/wiring/DefaultGatewayRequestHandlerTest.java`

---

### Task 1: Add governance API policy models

**Files:**
- Create: `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/governance/GovernancePolicy.java`
- Create: `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/governance/RateLimitPolicy.java`
- Create: `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/governance/CircuitBreakerPolicy.java`
- Create: `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/governance/TimeoutPolicy.java`
- Create: `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/governance/RetryPolicy.java`
- Create: `gateway-plugin/gateway-plugin-api/src/main/java/io/homeey/gateway/plugin/api/governance/DegradePolicy.java`

- [ ] Step 1: Write compile-time contract test usage in bootstrap tests (RED trigger via missing types)
- [ ] Step 2: Run targeted test/class compile to verify RED
- [ ] Step 3: Add immutable policy records with sensible defaults and `enabled` flags
- [ ] Step 4: Re-run compile for `gateway-plugin-api` and `gateway-bootstrap`

Run:
```powershell
mvn -q -pl gateway-bootstrap -am -DskipTests compile
```

### Task 2: Implement governance parser (config-driven, invalid->no-op)

**Files:**
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/governance/GovernancePolicyParser.java`
- Test: `gateway-bootstrap/src/test/java/io/homeey/gateway/bootstrap/governance/GovernancePolicyParserTest.java`

- [ ] Step 1: Write failing tests for parser behavior:
  - empty policy set => governance disabled
  - only one capability configured => only that one enabled
  - invalid number => capability disabled (no-op)
- [ ] Step 2: Run parser tests and verify RED
- [ ] Step 3: Implement parser with namespaced keys from spec
- [ ] Step 4: Re-run parser tests and verify GREEN

Run:
```powershell
mvn -q -pl gateway-bootstrap -am -Dtest=GovernancePolicyParserTest -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test
```

### Task 3: Implement local policy engine and state store

**Files:**
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/governance/PolicyEngine.java`
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/governance/PolicyDecision.java`
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/governance/LocalPolicyEngine.java`
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/governance/state/GovernanceStateStore.java`
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/governance/state/InMemoryGovernanceStateStore.java`
- Test: `gateway-bootstrap/src/test/java/io/homeey/gateway/bootstrap/governance/LocalPolicyEngineTest.java`

- [ ] Step 1: Write failing tests for:
  - rate limit reject
  - circuit open reject and half-open recovery
  - timeout + retry attempts
  - degrade response on governance reject
- [ ] Step 2: Run test and verify RED
- [ ] Step 3: Implement in-memory token bucket, breaker state machine, timeout/retry orchestration
- [ ] Step 4: Re-run test and verify GREEN

Run:
```powershell
mvn -q -pl gateway-bootstrap -am -Dtest=LocalPolicyEngineTest -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test
```

### Task 4: Integrate `TrafficGovernanceFilter` into request chain

**Files:**
- Create: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/governance/TrafficGovernanceFilter.java`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/wiring/DefaultGatewayRequestHandler.java`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/wiring/RuntimeFactory.java`
- Modify: `gateway-plugin/gateway-plugin-api/src/main/resources/META-INF/gateway/io.homeey.gateway.plugin.api.GatewayFilter`

- [ ] Step 1: Write failing integration test in handler:
  - no governance config => unchanged behavior
  - rate limit config => reject path observed
- [ ] Step 2: Run handler tests and verify RED
- [ ] Step 3: Add governance filter and insert before routing
- [ ] Step 4: Wire policy parser+engine into filter
- [ ] Step 5: Register filter in SPI resource and ensure order
- [ ] Step 6: Re-run handler tests and verify GREEN

Run:
```powershell
mvn -q -pl gateway-bootstrap -am -Dtest=DefaultGatewayRequestHandlerTest -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test
```

### Task 5: Full verification for changed modules

**Files:**
- Verify only

- [ ] Step 1: Run bootstrap module full tests
- [ ] Step 2: Run core and plugin api tests/compile checks
- [ ] Step 3: Run architecture tests for boundary safety
- [ ] Step 4: Run focused combined regression for request path

Run:
```powershell
mvn -q -pl gateway-plugin/gateway-plugin-api,gateway-core,gateway-bootstrap -am -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -q -pl architecture-tests -am -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test
```

---

## Plan Self-Review

1. Spec coverage:
   - 5 governance capabilities with no-op defaults: Task 2-4
   - engine/state/timer architecture: Task 3
   - filter-chain integration: Task 4
2. Placeholder scan:
   - no TODO/TBD placeholders
3. Type consistency:
   - parser keys and policy types are aligned with spec
Note:
- SPI default in API module should be `noop`; concrete modules register named implementations such as `local`.
- Do not register same extension name in both API and impl modules.
