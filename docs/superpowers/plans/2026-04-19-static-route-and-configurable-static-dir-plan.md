# Gateway Static Route + Configurable Static Dir Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add static-route support in gateway and make static resource root directory configurable via bootstrap config for easier route testing.

**Architecture:** Keep current route schema shape and use a convention-based static route (`upstreamService=static`). In request handling, branch to a local file-serving path before upstream forwarding. Add bootstrap config field for static resource root and enforce path traversal safety by normalizing/resolving under that root.

**Tech Stack:** Java 21, Maven, JUnit 5, existing gateway modules (`gateway-bootstrap`, `gateway-core`, `gateway-admin`).

---

### Task 1: Bootstrap Config Adds Static Resource Directory

**Files:**
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/config/BootstrapConfig.java`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/config/BootstrapConfigLoader.java`
- Modify: `gateway-bootstrap/src/test/java/io/homeey/gateway/bootstrap/config/BootstrapConfigLoaderTest.java`
- Modify: `gateway-bootstrap/src/main/resources/bootstrap.yaml`
- Modify: `gateway-bootstrap/src/test/resources/bootstrap.yaml`

- [ ] **Step 1: Write failing config loader tests**
- [ ] **Step 2: Run tests to verify red**
- [ ] **Step 3: Add `staticResourcesDir` field/default/loading logic**
- [ ] **Step 4: Run tests to verify green**

### Task 2: Snapshot Validation Accepts Static Route Convention

**Files:**
- Modify: `gateway-core/src/main/java/io/homeey/gateway/core/runtime/SnapshotSchemaValidator.java`
- Modify: `gateway-core/src/test/java/io/homeey/gateway/core/RouteSnapshotCodecTest.java`

- [ ] **Step 1: Write failing codec/validator test for static route**
- [ ] **Step 2: Run tests to verify red**
- [ ] **Step 3: Relax upstream validation for `upstreamService=static`**
- [ ] **Step 4: Run tests to verify green**

### Task 3: Request Handler Adds Static File Serving Branch

**Files:**
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/wiring/DefaultGatewayRequestHandler.java`
- Modify: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/wiring/RuntimeFactory.java`
- Add: `gateway-bootstrap/src/test/java/io/homeey/gateway/bootstrap/wiring/DefaultGatewayRequestHandlerTest.java`

- [ ] **Step 1: Write failing handler tests (200/404/405/path-traversal)**
- [ ] **Step 2: Run tests to verify red**
- [ ] **Step 3: Inject static dir into handler and implement static response path**
- [ ] **Step 4: Run tests to verify green**

### Task 4: Admin Publish Validation Compatibility

**Files:**
- Modify: `gateway-admin/src/main/java/io/homeey/gateway/admin/service/PublishService.java`
- Add/Modify: `gateway-admin/src/test/java/io/homeey/gateway/admin/` (if static-route validation test needed)

- [ ] **Step 1: Add failing test or assertion path for static route publish**
- [ ] **Step 2: Run targeted test to verify red**
- [ ] **Step 3: Adjust validation: allow static route convention**
- [ ] **Step 4: Run targeted test to verify green**

### Task 5: Verification

**Files:**
- None (command verification + summary)

- [ ] **Step 1: Run targeted module tests**
  - `mvn -q -pl gateway-bootstrap -am -Dtest=BootstrapConfigLoaderTest,DefaultGatewayRequestHandlerTest -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - `mvn -q -pl gateway-core -am -Dtest=RouteSnapshotCodecTest -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - `mvn -q -pl gateway-admin -am -Dtest=RouteControllerTest -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test`
- [ ] **Step 2: Provide static route JSON example + bootstrap YAML example in final report**
