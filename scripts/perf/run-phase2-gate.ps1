param(
    [string]$Target = "http://127.0.0.1:19080",
    [int]$Connections = 200,
    [int]$Threads = 8,
    [int]$DurationSeconds = 30,
    [double]$MinQps = 20000,
    [double]$MaxP99Ms = 20
)

$wrkCmd = Get-Command wrk -ErrorAction SilentlyContinue
if (-not $wrkCmd) {
    Write-Error "wrk command not found. Please install wrk and retry."
    exit 2
}

$outputPath = "scripts/perf/wrk-phase2-output.txt"
$luaScript = "scripts/perf/wrk-phase2.lua"

Write-Host "Running wrk phase2 benchmark..."
& wrk -t$Threads -c$Connections -d"$DurationSeconds"s -s $luaScript $Target | Tee-Object -FilePath $outputPath
$wrkExitCode = $LASTEXITCODE
if ($wrkExitCode -ne 0) {
    Write-Error "wrk failed with exit code $wrkExitCode"
    exit $wrkExitCode
}

Write-Host "Checking perf gate..."
& powershell -ExecutionPolicy Bypass -File scripts/perf/check-phase2-gate.ps1 `
    -WrkOutputPath $outputPath `
    -MinQps $MinQps `
    -MaxP99Ms $MaxP99Ms

exit $LASTEXITCODE
