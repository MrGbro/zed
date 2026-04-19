# Gateway Debug Mode Launcher
# Usage: .\debug-start.ps1

Write-Host "Starting Gateway in Debug Mode..." -ForegroundColor Green
Write-Host "Debug port: 5005" -ForegroundColor Yellow
Write-Host "Attach your IDE debugger to localhost:5005" -ForegroundColor Cyan
Write-Host ""

$JAVA_OPTS = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

# Compile first
Write-Host "Compiling project..." -ForegroundColor Green
mvn clean compile -DskipTests -q

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation failed!" -ForegroundColor Red
    exit 1
}

Write-Host "Starting application with debug enabled..." -ForegroundColor Green
java $JAVA_OPTS -cp "gateway-bootstrap/target/classes;gateway-core/target/classes;gateway-common/target/classes;$(mvn dependency:build-classpath -pl gateway-bootstrap -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout)" io.homeey.gateway.bootstrap.BootstrapApplication
