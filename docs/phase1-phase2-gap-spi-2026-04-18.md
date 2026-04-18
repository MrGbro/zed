# Phase1-2 Gap Analysis (SPI Focus)

- Date: 2026-04-18
- Baseline spec: `docs/superpowers/specs/2026-04-18-gateway-phase1-2-design.md`
- Goal of this doc: track SPI/pluginization progress and remaining gaps against Phase1-2

## 1) Current Status Summary

Implemented in this round:

1. Added SPI infrastructure in `gateway-common`:
   - `@SPI`
   - `@Activate`
   - `ExtensionLoader` (`getExtension/getDefaultExtension/getActivateEntries`)
2. Replaced bootstrap hardcoded wiring with SPI loading:
   - `ConfigProviderFactory` (default `nacos`)
   - `ServiceDiscoveryProviderFactory` (default `nacos`)
   - `TransportServerFactory` (default `netty`)
   - `ProxyClientFactory` (default `async-http-client`)
3. Registered implementation extensions via `META-INF/gateway/<fqcn>` for:
   - config/registry/transport/proxy
4. Added plugin runtime model and execution baseline:
   - `PluginBinding`, `PolicySet`, `FilterFailPolicy`, `FilterInvocation`
   - `FilterExecutionPlanCompiler`
   - `DefaultGatewayFilterChain` supports `FAIL_OPEN` and `FAIL_CLOSE`
5. Admin side now publishes plugin-related payload:
   - publish payload includes `pluginBindings` and `policySet`
   - `/api/routes/plugins` added
   - `/api/plugins` now loads from SPI registry rather than static hardcode
6. Governance baseline completed for publish workflow:
   - added publish validation for routes/bindings/policies
   - added publish history (`PublishRecord`) with config-center persistence and query API

## 2) Gap Table (Updated)

| Priority | Design requirement | Current implementation | Status / Gap |
|---|---|---|---|
| P0 | SPI pluginized loading for transport/config/registry/proxy | `RuntimeFactory` uses `ExtensionLoader` + factory SPI | Done |
| P0 | Support `@Activate(group, order, condition)` | `@Activate` supports `conditionKeys` and expression `conditions` (`key`,`!key`,`k=v`,`k!=v`) with `matchAll` policy | Partially done: complex boolean composition still missing |
| P0 | Filter plan precompile from publish config | `FilterExecutionPlanCompiler` compiles from route bindings + activate metadata | Done (baseline) |
| P0 | Admin publishes route + plugin governance data | `PublishRequest` + `pluginBindings` + `policySet` added | Done (baseline) |
| P1 | Dubbo-like extension directory and named/default load | `META-INF/gateway/*` + `@SPI` default + named load | Done |
| P1 | Plugin fail semantics `FAIL_OPEN/FAIL_CLOSE` | Implemented in `DefaultGatewayFilterChain` | Done (runtime baseline) |
| P1 | Plugin lifecycle `init/start/stop` | Added default lifecycle methods in `GatewayFilter`, wired in `BootstrapApplication` init/start/stop | Done (baseline) |
| P1 | Route engine true radix host-bucketed optimization | `RadixRouteLocator` upgraded to host-bucket + compressed radix lookup with longest-prefix preference | Done (baseline) |
| P1 | GatewayContext full fields (request/response/routeMatch/traceContext) | Added request/response/routeId/traceId baseline | Partially done: routeMatch/trace model incomplete |

## 3) Hardcoded Points Review

### 3.1 Bootstrap wiring

File: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/wiring/RuntimeFactory.java`

- Previous hardcoded `new` and `if/else` paths were removed.
- Current behavior:
  - resolve implementations by SPI name
  - use default SPI when type is blank
  - keep local fallback compatibility for Nacos unavailable path

Status: Completed.

### 3.2 Proxy selection hardcode

File: `gateway-bootstrap/src/main/java/io/homeey/gateway/bootstrap/wiring/ProxyClientFactory.java`

- Old hardcoded class in bootstrap removed.
- Replaced by `gateway-proxy-api` factory SPI + per-impl descriptors.

Status: Completed.

### 3.3 Plugin control-plane hardcode

File: `gateway-admin/src/main/java/io/homeey/gateway/admin/controller/PluginController.java`

- Static list removed.
- Plugin list now sourced from SPI extension metadata.

Status: Completed (baseline).  
Remaining gap: no persistent plugin catalog and no governance workflow.

## 4) Remaining Work for Full Phase1-2 Claim

1. Rich activation condition model:
   - baseline expression matching already supports `key`/`!key`/`k=v`/`k!=v`
   - still missing complex boolean composition (AND/OR nesting) and richer route/global merge rules
2. Route engine hardening:
   - current implementation upgraded to host-bucket + compressed radix baseline
   - perf benchmark gate scripts added (`scripts/perf/run-phase2-gate.ps1`, `scripts/perf/check-phase2-gate.ps1`), pending CI integration and regular benchmark data
3. Governance completeness:
   - `PublishRecord` persists to config center (`gateway.publish.records.json`), no dedicated DB/audit index yet
   - formal schema baseline added (`schemaVersion=2`) with v1/v2 compatibility decode, structure validation, and standardized snapshot error codes
4. E2E and regression coverage:
   - dynamic plugin change effect on live traffic
   - fail-open/close behavior integration tests

## 5) Verification Snapshot

This round executed:

1. `mvn -q -DskipTests compile`

Result: pass.

Per your request, no unit/integration tests were executed in this round.

