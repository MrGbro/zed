$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$adminPort = 19080
$nodePort = 19081

function Wait-HttpReady {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 60
    )

    $start = Get-Date
    while ((Get-Date) - $start -lt [TimeSpan]::FromSeconds($TimeoutSeconds)) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                return
            }
        } catch {
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Timeout waiting for $Url"
}

function Stop-Proc {
    param($Proc)
    if ($null -ne $Proc -and -not $Proc.HasExited) {
        Stop-Process -Id $Proc.Id -Force
    }
}

$adminProc = $null
$nodeProc = $null
$nodeStubFile = $null
try {
    Write-Host "[1/5] Starting admin..."
    $adminProc = Start-Process -FilePath "mvn" `
        -ArgumentList "-q -pl gateway-admin spring-boot:run -Dspring-boot.run.jvmArguments=`"-Dserver.port=$adminPort`"" `
        -WorkingDirectory $root `
        -PassThru
    Wait-HttpReady -Url "http://127.0.0.1:$adminPort/" -TimeoutSeconds 120

    Write-Host "[2/5] Starting node stub..."
    $nodeScript = @"
const http = require('http');
let version = 'v1';
const server = http.createServer((req, res) => {
  if (req.url === '/reload' && req.method === 'POST') {
    let body = '';
    req.on('data', c => body += c);
    req.on('end', () => {
      try { version = JSON.parse(body).version || version; } catch {}
      res.writeHead(200, {'Content-Type':'application/json'});
      res.end(JSON.stringify({ok:true, version}));
    });
    return;
  }
  if (req.url === '/version') {
    res.writeHead(200, {'Content-Type':'application/json'});
    res.end(JSON.stringify({version}));
    return;
  }
  res.writeHead(404); res.end('not found');
});
server.listen($nodePort, '127.0.0.1');
"@
    $nodeStubFile = Join-Path $env:TEMP "gateway-node-stub-$nodePort.js"
    Set-Content -Path $nodeStubFile -Value $nodeScript -Encoding ASCII
    $nodeProc = Start-Process -FilePath "node" `
        -ArgumentList $nodeStubFile `
        -WorkingDirectory $root `
        -PassThru
    Wait-HttpReady -Url "http://127.0.0.1:$nodePort/version"

    Write-Host "[3/5] Creating route and publishing via admin..."
    $existingRoutes = Invoke-RestMethod -Uri "http://127.0.0.1:$adminPort/api/routes" -Method Get
    if ($existingRoutes) {
        foreach ($route in $existingRoutes) {
            if ($route.id) {
                Invoke-RestMethod -Uri "http://127.0.0.1:$adminPort/api/routes/$($route.id)" -Method Delete | Out-Null
            }
        }
    }
    Invoke-RestMethod -Uri "http://127.0.0.1:$adminPort/api/routes/plugins" `
        -Method Post `
        -ContentType "application/json" `
        -Body "[]" | Out-Null

    $routeId = "smoke-r1-" + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $routeBody = @{
        id = $routeId
        host = "api.example.com"
        pathPrefix = "/orders"
        method = "GET"
        headers = @{}
        upstreamService = "order-service"
        upstreamPath = "/orders"
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "http://127.0.0.1:$adminPort/api/routes" -Method Post -ContentType "application/json" -Body $routeBody | Out-Null
    $publishBody = @{
        operator = "smoke"
        summary = "phase2 smoke publish"
        policySet = @{}
    } | ConvertTo-Json
    $publish = Invoke-RestMethod -Uri "http://127.0.0.1:$adminPort/api/routes/publish" `
        -Method Post `
        -ContentType "application/json" `
        -Body $publishBody
    if (-not $publish.version) {
        throw "Publish response missing version"
    }

    Write-Host "[4/5] Simulating node hot reload..."
    $reloadBody = @{ version = $publish.version } | ConvertTo-Json
    Invoke-RestMethod -Uri "http://127.0.0.1:$nodePort/reload" -Method Post -ContentType "application/json" -Body $reloadBody | Out-Null
    $nodeVersion = Invoke-RestMethod -Uri "http://127.0.0.1:$nodePort/version" -Method Get
    if ($nodeVersion.version -ne $publish.version) {
        throw "Node version mismatch: expected $($publish.version), got $($nodeVersion.version)"
    }

    Write-Host "[5/5] Perf baseline (lightweight synthetic)..."
    $count = 30
    $ok = 0
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    for ($i = 0; $i -lt $count; $i++) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:$adminPort/api/routes" -UseBasicParsing -TimeoutSec 2
            if ($r.StatusCode -eq 200) { $ok++ }
        } catch {
        }
    }
    $sw.Stop()
    $qps = [Math]::Round(($ok / ($sw.ElapsedMilliseconds / 1000.0)), 2)
    Write-Host "SMOKE_PASS version=$($publish.version) nodeVersion=$($nodeVersion.version) ok=$ok/$count qps=$qps"
} finally {
    Stop-Proc $nodeProc
    Stop-Proc $adminProc
    if ($nodeStubFile -and (Test-Path $nodeStubFile)) {
        Remove-Item -LiteralPath $nodeStubFile -Force
    }
}
