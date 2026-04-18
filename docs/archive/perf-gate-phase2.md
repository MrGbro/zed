# Phase2 Perf Gate

This document defines the baseline performance gate for Phase2.

## 1) Gate Targets

1. `QPS >= 20000`
2. `P99 <= 20ms`

## 2) Run Locally

1. Ensure gateway is running (for example `http://127.0.0.1:19080`).
2. Ensure `wrk` is installed and available in `PATH`.
3. Run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/perf/run-phase2-gate.ps1
```

Optional parameters:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/perf/run-phase2-gate.ps1 `
  -Target "http://127.0.0.1:19080" `
  -Connections 200 `
  -Threads 8 `
  -DurationSeconds 30 `
  -MinQps 20000 `
  -MaxP99Ms 20
```

## 3) Output

1. Raw wrk output is saved to:
   - `scripts/perf/wrk-phase2-output.txt`
2. Gate check script:
   - `scripts/perf/check-phase2-gate.ps1`
3. Exit code:
   - `0`: pass
   - non-zero: fail
