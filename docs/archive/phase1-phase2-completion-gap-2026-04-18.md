# Phase1-2 Completed vs Pending Checklist (Code-Based)

- Date: 2026-04-18
- Scope baseline: `docs/superpowers/specs/2026-04-18-gateway-phase1-2-design.md`
- Assessment method: static code review only (no test execution in this round)

## 1) Completed Feature List

| ID | Feature | Current status | Evidence |
|---|---|---|---|
| C01 | Maven multi-module skeleton established | Done | `pom.xml`, `gateway-*/pom.xml`, `architecture-tests/pom.xml` |
| C02 | Core/common basic models are in place | Done | `gateway-common/src/main/java/io/homeey/gateway/common/error/GatewayError.java`, `.../ErrorCategory.java`, `.../context/Attributes.java` |
| C03 | API contracts with `CompletionStage` exist | Done (baseline) | `gateway-plugin-api/GatewayFilter*.java`, `gateway-transport-api/TransportServer.java`, `gateway-config-api/ConfigProvider.java`, `gateway-registry-api/ServiceDiscoveryProvider.java` |
| C04 | Async filter chain execution baseline is implemented | Done (baseline) | `gateway-core/src/main/java/io/homeey/gateway/core/filter/DefaultGatewayFilterChain.java`, `.../FilterExecutionPlan.java` |
| C05 | Route matching baseline is implemented | Done (baseline) | `gateway-core/src/main/java/io/homeey/gateway/core/route/RadixRouteLocator.java`, `RouteDefinition.java`, `RouteTableSnapshot.java` |
| C06 | Runtime snapshot atomic switch baseline exists | Done (baseline) | `gateway-core/src/main/java/io/homeey/gateway/core/runtime/RuntimeSnapshotManager.java`, `GatewayRuntime.java` |
| C07 | Transport layer Netty module is available and start/stop capable | Done (baseline) | `gateway-transport/gateway-transport-netty/src/main/java/.../NettyTransportServer.java` |
| C08 | Bootstrap wiring baseline exists | Done (baseline) | `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/BootstrapApplication.java`, `.../wiring/RuntimeFactory.java`, `.../config/BootstrapConfig.java` |
| C09 | Admin service + simple web console baseline exists | Done (baseline) | `gateway-admin/src/main/java/.../AdminApplication.java`, `controller/RouteController.java`, `controller/PluginController.java`, `service/PublishService.java`, `src/main/resources/static/index.html` |
| C10 | Nacos config/discovery adapter modules exist | Done (simulated baseline) | `gateway-config-nacos/NacosConfigProvider.java`, `gateway-registry-nacos/NacosServiceDiscoveryProvider.java` |
| C11 | Error mapping/metrics/trace-id generator baseline exists | Done (baseline) | `gateway-core/src/main/java/io/homeey/gateway/core/error/ErrorMapper.java`, `.../metrics/GatewayMetrics.java`, `.../tracing/TraceContextFactory.java` |
| C12 | Architecture guard tests are defined | Done | `architecture-tests/src/test/java/io/homeey/gateway/arch/DependencyRulesTest.java` |
| C13 | Smoke/perf scripts and acceptance checklist are present | Done (artifact level) | `scripts/e2e/run-phase2-smoke.ps1`, `scripts/perf/wrk-phase1.lua`, `scripts/perf/wrk-phase2.lua`, `docs/superpowers/checklists/phase1-phase2-acceptance.md` |

## 2) Pending Feature Table (Against Design Target)

| Priority | Pending item | Current gap in code | Impact |
|---|---|---|---|
| P0 | Data plane real gateway forwarding pipeline | **Partial**: transport is handler-driven and supports proxy request flow; full e2e with real upstream still needs dedicated integration run | Core capability mostly wired, needs integration proof |
| P0 | HTTP/2 support in data plane | **Implemented** in transport via `HttpProtocol.HTTP11, HttpProtocol.H2C`; pending explicit h2 test case | Feature wired, test evidence incomplete |
| P0 | Real Nacos integration (SDK/client) | **Implemented** with Nacos SDK in config/registry; fallback to in-memory kept | Integrated with fallback semantics |
| P0 | Admin publish -> config center -> node hot reload end-to-end | **Partial**: admin publish now writes `gateway.routes.json@GATEWAY`; bootstrap subscribes and updates snapshot; full e2e smoke pending rerun | Core loop connected, e2e evidence pending |
| P0 | Gateway runtime lifecycle `init -> start -> stop` with graceful shutdown orchestration | **Implemented baseline**: explicit `init/start/stop` added; graceful window is baseline-level (no advanced drain metric hooks yet) | Lifecycle capability available |
| P1 | Route engine true Radix implementation | `RadixRouteLocator` is linear list matching, not host-bucket + radix tree | Performance/scalability target risk |
| P1 | `GatewayContext` full model constraints | Current context mainly `attributes`; missing explicit request/response/routeMatch/traceContext model fields | Plugin contract and data flow are weak |
| P1 | Plugin system activation/sorting/conditions (`@Activate`) | No activation metadata parser or compile-time execution plan from publish config | Pluginized behavior not complete |
| P1 | Plugin execution semantics `FAIL_OPEN`/`FAIL_CLOSE` full support | Current chain maps exceptions to error; no full configurable fail-open/close policy | Error behavior control incomplete |
| P1 | Control plane models and APIs (`RouteDefinition/PluginBinding/PolicySet/PublishRecord`) | Admin endpoints are minimal and in-memory; no complete domain models/persistence/versioned records | Governance capability incomplete |
| P1 | Dist packaging and runnable distribution | `gateway-dist/pom.xml` is placeholder `pom`; no packaging assembly/runtime profile | Delivery artifact incomplete |
| P1 | Real observability integration (Micrometer/OpenTelemetry + `/metrics`) | Current metrics/tracing are local helpers, no exporter/endpoint integration | Ops visibility incomplete |
| P2 | Retry/circuit-breaker/backpressure strategy set | No implementation of retry budget/backoff, circuit state machine, concurrency limits, overload policy | Resilience capabilities pending |
| P2 | Upstream load balancing and service instance selection | No routing filter selecting instances from service discovery in data path | Service governance capability pending |
| P2 | Plugin ecosystem modules (auth/ratelimit/observability/rewrite etc.) | Only `gateway-plugin-api` exists; implementation modules not present | Extensibility ecosystem pending |
| P2 | CI performance gates and automated acceptance flow | Repo contains scripts/checklist, but no CI gate config for perf thresholds found | Quality gate incomplete |

## 3) Notes for Cross-Checking

- This checklist distinguishes:
  - `Done`: code/module exists and has baseline behavior.
  - `Pending`: still not aligned with design target or only partially simulated.
- This round intentionally does not execute unit/integration tests per request.

## 4) This Round Update (2026-04-18)

Key implemented changes:

1. Added real Nacos client dependencies and fallback providers in config/registry modules.
2. Extended config/discovery API contracts to support publish/register behaviors.
3. Added dedicated `gateway-proxy` aggregate and pluggable proxy client contract with three implementations (`async-http-client` default, `reactor-netty`, `okhttp`).
4. Upgraded transport server to handler-driven request path and enabled HTTP/1.1 + h2c protocols.
5. Added bootstrap runtime wiring for providers/proxy/handler and explicit lifecycle (`init/start/stop`).
6. Added route snapshot codec and runtime snapshot switch hook for route updates.
7. Connected admin publish API to Nacos `gateway.routes.json@GATEWAY`.

Verification status in this round:

1. `mvn -q -DskipTests compile` : PASS
2. `mvn -q -pl gateway-admin -am -Dtest=RouteControllerTest ... test` : PASS
3. `gateway-transport` targeted test was blocked intermittently by repository/network access in this environment, so transport runtime verification should be re-run in a stable network context.
