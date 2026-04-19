# Gateway Observability Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `docs/obs-arch.md` aligned observability architecture with `gateway-observe` module family, OTel default SPI implementation, data-plane `/metrics`, and split application/access logs.

**Architecture:** Introduce an observability SPI layer (`gateway-observe-api`) and a default OTel bridge implementation (`gateway-observe-otel`). Wire observe provider lifecycle in bootstrap runtime and instrument gateway request handling in fail-open mode. Keep core/bootstrap decoupled from backend-specific exporter details through SPI.

**Tech Stack:** Java 21, Maven multi-module, OpenTelemetry SDK + OTLP exporter, Logback, JUnit 5.

---

## 0. File Structure

### 0.1 Build and module graph
- Modify: `pom.xml`
- Create: `gateway-observe/pom.xml`
- Create: `gateway-observe/gateway-observe-api/pom.xml`
- Create: `gateway-observe/gateway-observe-otel/pom.xml`

### 0.2 Observe API
- Create: `gateway-observe/gateway-observe-api/src/main/java/io/homeey/gateway/observe/api/ObserveOptions.java`
- Create: `gateway-observe/gateway-observe-api/src/main/java/io/homeey/gateway/observe/api/ObserveProvider.java`
- Create: `gateway-observe/gateway-observe-api/src/main/java/io/homeey/gateway/observe/api/ObserveProviderFactory.java`
- Create: `gateway-observe/gateway-observe-api/src/main/java/io/homeey/gateway/observe/api/RequestObservation.java`
- Create: `gateway-observe/gateway-observe-api/src/main/java/io/homeey/gateway/observe/api/NoopObserveProvider.java`

### 0.3 OTel default implementation
- Create: `gateway-observe/gateway-observe-otel/src/main/java/io/homeey/gateway/observe/otel/OtelObserveProviderFactory.java`
- Create: `gateway-observe/gateway-observe-otel/src/main/java/io/homeey/gateway/observe/otel/OtelObserveProvider.java`
- Create: `gateway-observe/gateway-observe-otel/src/main/resources/META-INF/gateway/io.homeey.gateway.observe.api.ObserveProviderFactory`
- Create: `gateway-observe/gateway-observe-otel/src/test/java/io/homeey/gateway/observe/otel/OtelObserveProviderTest.java`

### 0.4 Bootstrap wiring
- Modify: `gateway-bootstrap/pom.xml`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/config/BootstrapConfig.java`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/config/BootstrapConfigLoader.java`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/wiring/RuntimeFactory.java`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/wiring/DefaultGatewayRequestHandler.java`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/BootstrapApplication.java`
- Modify: `gateway-bootstrap/src/main/resources/bootstrap.yaml`
- Modify: `gateway-bootstrap/src/test/resources/bootstrap.yaml`
- Create: `gateway-bootstrap/src/main/resources/logback.xml`
- Modify: `gateway-bootstrap/src/test/java/io/homeey/gateway/bootstrap/config/BootstrapConfigLoaderTest.java`
- Modify: `gateway-bootstrap/src/test/java/io/homeey/gateway/bootstrap/wiring/DefaultGatewayRequestHandlerTest.java`

### 0.5 Architecture guard
- Modify: `architecture-tests/src/test/java/io/homeey/gateway/arch/DependencyRulesTest.java`

---

### Task 1: Create `gateway-observe` module family

**Files:**
- Modify: `pom.xml`
- Create: `gateway-observe/pom.xml`
- Create: `gateway-observe/gateway-observe-api/pom.xml`
- Create: `gateway-observe/gateway-observe-otel/pom.xml`

- [ ] Step 1: Add new modules into root and aggregator poms
- [ ] Step 2: Run build sanity for new module graph

Run:
```powershell
mvn -q -DskipTests validate
```

---

### Task 2: Implement Observe SPI contracts (API)

**Files:**
- Create: `gateway-observe/gateway-observe-api/src/main/java/io/homeey/gateway/observe/api/ObserveOptions.java`
- Create: `gateway-observe/gateway-observe-api/src/main/java/io/homeey/gateway/observe/api/ObserveProvider.java`
- Create: `gateway-observe/gateway-observe-api/src/main/java/io/homeey/gateway/observe/api/ObserveProviderFactory.java`
- Create: `gateway-observe/gateway-observe-api/src/main/java/io/homeey/gateway/observe/api/RequestObservation.java`
- Create: `gateway-observe/gateway-observe-api/src/main/java/io/homeey/gateway/observe/api/NoopObserveProvider.java`

- [ ] Step 1: Add API contracts and default no-op implementation
- [ ] Step 2: Ensure SPI default annotation is `@SPI("otel")`
- [ ] Step 3: Compile api module

Run:
```powershell
mvn -q -pl gateway-observe/gateway-observe-api -am -DskipTests compile
```

---

### Task 3: Implement default OTel provider and SPI registration

**Files:**
- Create: `gateway-observe/gateway-observe-otel/src/main/java/io/homeey/gateway/observe/otel/OtelObserveProviderFactory.java`
- Create: `gateway-observe/gateway-observe-otel/src/main/java/io/homeey/gateway/observe/otel/OtelObserveProvider.java`
- Create: `gateway-observe/gateway-observe-otel/src/main/resources/META-INF/gateway/io.homeey.gateway.observe.api.ObserveProviderFactory`
- Create: `gateway-observe/gateway-observe-otel/src/test/java/io/homeey/gateway/observe/otel/OtelObserveProviderTest.java`

- [ ] Step 1: Write failing test for metrics snapshot + trace id generation + fail-open lifecycle
- [ ] Step 2: Run test to verify RED
- [ ] Step 3: Implement OTel provider with OTLP config support and Prometheus-compatible metrics snapshot
- [ ] Step 4: Register SPI resource (`otel=...Factory`)
- [ ] Step 5: Run module tests to verify GREEN

Run:
```powershell
mvn -q -pl gateway-observe/gateway-observe-otel -am -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test
```

---

### Task 4: Wire observe provider into bootstrap runtime and request handling

**Files:**
- Modify: `gateway-bootstrap/pom.xml`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/config/BootstrapConfig.java`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/config/BootstrapConfigLoader.java`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/wiring/RuntimeFactory.java`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/wiring/DefaultGatewayRequestHandler.java`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/BootstrapApplication.java`
- Modify: `gateway-bootstrap/src/main/resources/bootstrap.yaml`
- Modify: `gateway-bootstrap/src/test/resources/bootstrap.yaml`

- [ ] Step 1: Extend bootstrap config model with observe fields (`observeProviderType`, `otlpEndpoint`, `otlpHeaders`, `observeServiceName`, `observeExportIntervalMillis`, `metricsPath`, `accessLogEnabled`)
- [ ] Step 2: Load observe provider via SPI in RuntimeFactory with fallback to Noop
- [ ] Step 3: Add provider lifecycle calls in BootstrapApplication init/start/stop
- [ ] Step 4: Instrument DefaultGatewayRequestHandler (request begin/end, route/upstream/error markers, trace header propagation)
- [ ] Step 5: Add `/metrics` short-circuit in data plane request handler using provider snapshot
- [ ] Step 6: Update config loader tests and request handler tests

Run:
```powershell
mvn -q -pl gateway-bootstrap -am -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test
```

---

### Task 5: Add log split (`application.log` and `access.log`)

**Files:**
- Create: `gateway-bootstrap/src/main/resources/logback.xml`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/BootstrapApplication.java`
- Modify: `gateway-observe/gateway-observe-otel/src/main/java/io/homeey/gateway/observe/otel/OtelObserveProvider.java`

- [ ] Step 1: Add logback appenders and logger routing (`io.homeey.gateway.access` -> `access.log`, root/system -> `application.log`)
- [ ] Step 2: Ensure startup/lifecycle logs are emitted via normal class logger
- [ ] Step 3: Ensure access logs are emitted only via dedicated access logger

---

### Task 6: Update architecture constraints and full verification

**Files:**
- Modify: `architecture-tests/src/test/java/io/homeey/gateway/arch/DependencyRulesTest.java`

- [ ] Step 1: Update architecture rule set to include observe implementation package in API boundary checks
- [ ] Step 2: Run architecture tests
- [ ] Step 3: Run combined regression suite for changed modules

Run:
```powershell
mvn -q -pl architecture-tests -am -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -q -pl gateway-observe/gateway-observe-otel,gateway-bootstrap,gateway-core,gateway-transport/gateway-transport-netty,gateway-transport/gateway-transport-vertx,gateway-transport/gateway-transport-reactor -am -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test
```

---

## Plan Self-Review

1. Spec coverage:
   - module split/SPI/default provider: Task 1-3
   - bootstrap wiring and `/metrics`: Task 4
   - log split: Task 5
   - architecture guard and verification: Task 6
2. Placeholder scan:
   - no TODO/TBD placeholders remain
3. Type consistency:
   - bootstrap config keys and observe API names are aligned across tasks

