# zed

Java 21-based gateway implemented as a Maven multi-module project.

## Modules
- `gateway-common`: shared utilities and SPI runtime (`ExtensionLoader`)
- `gateway-core`: request pipeline and filter execution
- `gateway-bootstrap`: runtime assembly and app startup
- `gateway-admin`: Spring Boot admin plane
- `gateway-dist`: distribution packaging
- `gateway-transport/*`: transport APIs and implementations
- `gateway-proxy/*`: upstream proxy APIs and implementations
- `gateway-plugin/gateway-plugin-api`: plugin contracts
- `gateway-observe/*`: observability APIs and implementations
- `gateway-governance/*`: traffic governance engine and capabilities
- `gateway-config/*`: dynamic config APIs and implementations
- `gateway-registry/*`: service registry/discovery APIs and implementations
- `architecture-tests`: architecture boundary tests

## Governance Modules
- `gateway-governance-api`
- `gateway-governance-engine`
- `gateway-governance-state-local`
- `gateway-governance-ratelimit-local`
- `gateway-governance-ratelimit-sentinel`
- `gateway-governance-circuitbreaker-local`
- `gateway-governance-retry-local`
- `gateway-governance-timeout-local`
- `gateway-governance-degrade-local`

## Build And Test
- Validate: `mvn -q -DskipTests validate`
- Compile all: `mvn -DskipTests compile`
- Full tests: `mvn test`
- Governance tests: `mvn -q -pl gateway-governance -am -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test`
- Bootstrap wiring test: `mvn -q -pl gateway-bootstrap -am -Dtest=DefaultGatewayRequestHandlerTest -DfailIfNoTests=false "-Dsurefire.failIfNoSpecifiedTests=false" test`

## Bootstrap Config
`gateway-bootstrap` loads startup config in this order:
1. JVM system property `gateway.bootstrap.config` (external file path)
2. classpath resource `bootstrap.yaml` (`gateway-bootstrap/src/main/resources/bootstrap.yaml`)
3. built-in defaults in `BootstrapConfig.defaultConfig()`

Example:
`mvn -pl gateway-bootstrap -DskipTests exec:java -Dexec.mainClass=io.homeey.gateway.bootstrap.BootstrapApplication -Dgateway.bootstrap.config=F:/config/bootstrap.yaml`

## RateLimit Provider Configuration
RateLimit now supports provider-based SPI dispatch through `RateLimitPolicyHandler`.

- `local` provider: `gateway-governance-ratelimit-local`
- `sentinel` provider: `gateway-governance-ratelimit-sentinel`

Common policy keys:
- `governance.enabled`
- `governance.ratelimit.enabled`
- `governance.ratelimit.provider` (`local` or `sentinel`)
- `governance.ratelimit.failureMode` (`fail-open` or `fail-close`)
- `governance.ratelimit.qps`
- `governance.ratelimit.burst`
- `governance.ratelimit.keyType` (`route` or `ip` or `header`)
- `governance.ratelimit.keyHeader` (used when `keyType=header`)

Example policy entries:
```yaml
governance:
  enabled: true
  ratelimit:
    enabled: true
    provider: sentinel
    failureMode: fail-close
    qps: 200
    burst: 200
    keyType: route
```
