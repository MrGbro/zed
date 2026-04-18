# Phase1-2 Acceptance Checklist

- Date: 2026-04-18
- Scope: gateway phase1-2 runnable baseline

## Functional

- [x] Admin service can boot and expose `/api/routes`.
- [x] Route create API works (`POST /api/routes`).
- [x] Publish API works and returns `version`.
- [x] Node receives simulated hot reload and updates version.
- [x] Nacos adapter tests pass for config and service discovery.

## Performance Baseline

- [x] Smoke script prints lightweight qps metric.
- [x] `scripts/perf/wrk-phase1.lua` prepared for phase1 route list baseline.
- [x] `scripts/perf/wrk-phase2.lua` prepared for mixed read/publish baseline.

## Verification Commands

```powershell
powershell -File scripts/e2e/run-phase2-smoke.ps1
mvn -q -pl gateway-core -am -Dtest=FilterChainTest,RadixRouteLocatorTest,RuntimeSnapshotManagerTest,ErrorMapperTest -DfailIfNoTests=false '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -q -pl gateway-admin -am -Dtest=RouteControllerTest -DfailIfNoTests=false '-Dsurefire.failIfNoSpecifiedTests=false' test
```
