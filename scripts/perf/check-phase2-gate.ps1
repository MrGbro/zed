param(
    [string]$WrkOutputPath = "scripts/perf/wrk-phase2-output.txt",
    [double]$MinQps = 20000,
    [double]$MaxP99Ms = 20
)

if (-not (Test-Path $WrkOutputPath)) {
    Write-Error "wrk output file not found: $WrkOutputPath"
    exit 2
}

$content = Get-Content $WrkOutputPath -Raw

$qps = $null
$p99Ms = $null

if ($content -match "Requests/sec:\s+([0-9]+(?:\.[0-9]+)?)") {
    $qps = [double]$matches[1]
}

if ($content -match "99%\s+([0-9]+(?:\.[0-9]+)?)(us|ms|s)") {
    $value = [double]$matches[1]
    $unit = $matches[2]
    switch ($unit) {
        "us" { $p99Ms = $value / 1000.0 }
        "ms" { $p99Ms = $value }
        "s"  { $p99Ms = $value * 1000.0 }
    }
}

if ($null -eq $qps) {
    Write-Error "Cannot parse Requests/sec from wrk output"
    exit 3
}
if ($null -eq $p99Ms) {
    Write-Error "Cannot parse p99 latency from wrk output (need '99% ...')"
    exit 4
}

Write-Host ("Parsed Metrics => QPS={0}, P99={1}ms" -f $qps, [Math]::Round($p99Ms, 3))

$okQps = $qps -ge $MinQps
$okP99 = $p99Ms -le $MaxP99Ms

if ($okQps -and $okP99) {
    Write-Host ("PERF GATE PASS (QPS>={0}, P99<={1}ms)" -f $MinQps, $MaxP99Ms)
    exit 0
}

Write-Error ("PERF GATE FAIL: required QPS>={0}, P99<={1}ms, actual QPS={2}, P99={3}ms" -f $MinQps, $MaxP99Ms, $qps, [Math]::Round($p99Ms, 3))
exit 1
