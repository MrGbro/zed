# Phase1-Phase2 Gap Assessment (2026-04-19, refreshed)

- Date: 2026-04-19
- Baseline spec: `docs/superpowers/specs/2026-04-18-gateway-phase1-2-design.md`
- Scope: Re-check whether Phase1 + Phase2 requirements are fully implemented in current codebase

## 1) Verdict

Phase1/Phase2 are **functionally completed at baseline**, with one remaining acceptance evidence gap:

1. Core functional requirements of Phase1/2 are implemented and verified by fresh module tests.
2. E2E smoke flow is runnable and passes after fixing script/API contract drift.
3. Performance acceptance target (`20k+ QPS`, `P99 < 20ms`, `20k-50k` band) is still **not proven in this environment** because `wrk` is unavailable.

## 2) Fresh Evidence (This Round)

### 2.1 E2E smoke

Command:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/e2e/run-phase2-smoke.ps1
```

Result:

1. Passed with `SMOKE_PASS`.
2. Output: `version` and `nodeVersion` matched, `ok=30/30`, synthetic `qps=25.84`.

### 2.2 Phase1/2 module verification

Command:

```powershell
mvn -q -pl gateway-core,gateway-bootstrap,gateway-admin,gateway-config/gateway-config-nacos,gateway-registry/gateway-registry-nacos,gateway-transport/gateway-transport-netty,gateway-transport/gateway-transport-vertx,gateway-transport/gateway-transport-reactor -am -DfailIfNoTests=false '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

1. Passed (exit code 0).
2. Confirms the earlier `DefaultGatewayFilterChain` fail-open recursion regression is resolved in current code.
3. Confirms transport module set now includes `netty + vertx + reactor`.

### 2.3 Performance gate

Command:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/perf/run-phase2-gate.ps1
```

Result:

1. Failed early with environment blocker: `wrk command not found`.
2. No valid QPS/P99 benchmark artifact generated in this run.

## 3) Requirement-by-Requirement Matrix

Reference sections:

1. Spec scope: section 2.1
2. Phase1/2 difference: section 5.1
3. Acceptance: sections 12.2, 12.3

| Requirement (Spec) | Current status | Evidence | Conclusion |
|---|---|---|---|
| Phase1: HTTP/1.1 static forwarding usable | Implemented | `gateway-transport-netty` tests + smoke flow | Done (baseline) |
| Phase1: Basic filter chain works | Implemented | `gateway-core` tests pass, fail-open regression fixed | Done |
| Phase1: Performance baseline (`20k+`, `P99 < 20ms`) | Script ready, not executed successfully in current env | `run-phase2-gate.ps1` blocked by missing `wrk` | **Not verified** |
| Phase2: Radix routing usable | Implemented | `RadixRouteLocator` tests in `gateway-core` set | Done |
| Phase2: Pluginized loading/sorting/conditions usable | Implemented baseline | SPI + execution plan tests and runtime wiring | Done (baseline) |
| Phase2: Nacos config/discovery hot update usable | Implemented | `gateway-config-nacos` + `gateway-registry-nacos` tests | Done |
| Phase2: Admin OpenAPI + simple web console usable | Implemented | `gateway-admin` tests + smoke API flow | Done |
| Phase2: Steady-state perf band (`20k-50k`) | Script ready, evidence absent | no `wrk` output artifact yet | **Not verified** |
| Scope exclusion handling (full governance console not in Phase1-2) | As expected | Spec section 2.2 | Not a Phase1/2 gap |

## 4) Remaining Gaps (Current)

Priority P0/P1:

1. **P0 Acceptance evidence gap**: run perf gate in an environment with `wrk`, then archive output artifact (QPS/P99).
2. **P1 Documentation evidence gap**: add an acceptance report that links smoke + perf outputs and records machine/profile details.

No blocking functional regression remains in current verification scope.

## 5) Actions Completed This Round

1. Fixed smoke script contract mismatch for `/api/routes/publish` request.
2. Fixed smoke script route payload schema drift (`host/pathPrefix/upstreamService/...`).
3. Added smoke pre-clean step (clear existing routes/bindings) to avoid historical config contamination.
4. Re-ran smoke and module test matrix to collect fresh evidence.

## 6) Next Closure Steps

1. Install `wrk` on benchmark host.
2. Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/perf/run-phase2-gate.ps1
```

3. Archive generated output (`scripts/perf/wrk-phase2-output.txt`) to docs and update this gap doc to fully closed.

## 7) Archive Update

The previous pre-fix assessment is archived:

1. `docs/archive/phase1-phase2-gap-2026-04-19-pre-fix.md`

Existing archives retained:

1. `docs/archive/phase1-phase2-completion-gap-2026-04-18.md`
2. `docs/archive/phase1-phase2-gap-spi-2026-04-18.md`
