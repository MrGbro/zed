# Traffic Governance Design (RateLimit/Circuit/Degrade/Timeout/Retry)

- Date: 2026-04-19
- Baseline: `docs/governance-2.md`
- Scope: Data-plane governance execution for `限流/熔断/降级/超时/重试`

## 1. Goals

1. Implement real runtime governance execution in data plane, not only config carry-through.
2. Keep architecture aligned to `Gateway Filter Chain -> Traffic Governance Filter -> Policy Engine -> State Store -> Scheduler/Timer`.
3. Keep all capabilities config-driven with strict no-op default:
   - if config is absent or invalid => governance capability is bypassed.
4. Preserve existing route/publish snapshot compatibility (schemaVersion=2 remains readable).
5. Keep extension boundaries clear so different implementations can be added later.

## 2. Architecture

## 2.1 Data-plane execution path

1. `DefaultGatewayRequestHandler` builds context and route attributes.
2. `FilterExecutionPlanCompiler` includes `TrafficGovernanceFilter` in route group.
3. `TrafficGovernanceFilter`:
   - parses route governance config from `PolicySet`
   - builds an immutable governance decision context
   - runs `PolicyEngine` pipeline in fixed order:
     1. rate limit (admission)
     2. circuit breaker pre-check
     3. timeout + retry wrapper around downstream invocation
     4. circuit breaker record outcome
     5. degrade fallback mapping on reject/failure
4. Routing filter keeps forwarding responsibility only.

## 2.2 Module boundaries

1. `gateway-plugin-api`
   - governance API contracts and config model (pure API, no runtime state impl)
2. `gateway-bootstrap`
   - concrete governance filter and default local policy engine/state store
3. `gateway-core`
   - unchanged core filter chain mechanics; only consumes filters via SPI

## 2.3 Extensibility

1. Governance engine dependencies are interfaces:
   - `RateLimiter`
   - `CircuitBreaker`
   - `RetryExecutor`
   - `TimeoutExecutor`
   - `DegradeHandler`
   - `GovernanceStateStore`
2. Bootstrap provides default in-memory implementations.
3. Future distributed implementations can be introduced without touching filter contract.
4. SPI defaults use no-op implementations in `gateway-governance-api`:
   - `GovernancePolicyParser @SPI("noop")`
   - `GovernanceExecutor @SPI("noop")`
5. `gateway-governance-local` registers explicit `local` implementations, and local filter resolves `local` by name to avoid extension-order ambiguity.

## 3. Configuration Model (PolicySet keys)

Route-level `policySet.entries` keys (flat namespaced keys):

1. Global switch
   - `governance.enabled` (bool, default false)
2. Rate limit
   - `governance.ratelimit.enabled` (bool, default false)
   - `governance.ratelimit.qps` (double > 0)
   - `governance.ratelimit.burst` (int >= 1, default = ceil(qps))
   - `governance.ratelimit.key` (`route` | `ip`, default `route`)
3. Circuit breaker
   - `governance.circuit.enabled` (bool, default false)
   - `governance.circuit.failureRateThreshold` (0-100, default 50)
   - `governance.circuit.minimumCalls` (int >= 1, default 20)
   - `governance.circuit.openDurationMillis` (long >= 1, default 10000)
   - `governance.circuit.halfOpenMaxCalls` (int >= 1, default 5)
4. Timeout
   - `governance.timeout.enabled` (bool, default false)
   - `governance.timeout.durationMillis` (long >= 1)
5. Retry
   - `governance.retry.enabled` (bool, default false)
   - `governance.retry.maxAttempts` (int >= 1, default 1)
   - `governance.retry.backoffMillis` (long >= 0, default 0)
   - `governance.retry.retryOnStatuses` (csv string, default `502,503,504`)
6. Degrade
   - `governance.degrade.enabled` (bool, default false)
   - `governance.degrade.status` (int, default 503)
   - `governance.degrade.body` (string, default `service degraded`)
   - `governance.degrade.contentType` (string, default `text/plain; charset=UTF-8`)

No-op rules:

1. `governance.enabled != true` => full bypass.
2. Per capability `enabled != true` => that capability bypass.
3. Invalid values => treated as not configured for that capability (fail-open parse).

## 4. Policy Engine Behavior

## 4.1 Rate limit

1. Token-bucket per key in local memory.
2. On reject:
   - if degrade enabled => return degrade response.
   - else return `429 too many requests`.

## 4.2 Circuit breaker

1. States: `CLOSED`, `OPEN`, `HALF_OPEN`.
2. `OPEN` rejects requests before routing.
3. Transition:
   - `CLOSED -> OPEN` when failure rate threshold reached with minimum calls.
   - `OPEN -> HALF_OPEN` after `openDurationMillis`.
   - `HALF_OPEN -> CLOSED` on enough successful probes.
   - `HALF_OPEN -> OPEN` on probe failure.
4. On open reject:
   - degrade response if configured, else `503 circuit open`.

## 4.3 Timeout

1. Wrap downstream `CompletionStage` with timer future.
2. Timeout completes exceptionally with governance timeout exception.
3. Timeout can be retried if retry policy allows.

## 4.4 Retry

1. Retry wraps downstream execution.
2. Retry trigger:
   - exception
   - or status in `retryOnStatuses`
3. Maximum attempts includes first attempt.
4. Backoff is fixed sleep via scheduler for now.

## 4.5 Degrade

1. Applies on governance rejection/failure categories:
   - rate limited
   - circuit open
   - timeout exhausted
   - retry exhausted on retryable failures
2. Builds immediate fallback `HttpResponseMessage`.
3. If degrade disabled, return default error status per category.

## 5. Error and Metrics Mapping

1. Context attributes add governance markers:
   - `governance.rate_limited`
   - `governance.circuit_open`
   - `governance.timeout`
   - `governance.retry.attempts`
   - `governance.degraded`
2. `GatewayMetrics` counters can be marked from governance filter:
   - timeout/retry already exist
   - add optional counters later without breaking this phase.

## 6. Compatibility and Safety

1. Existing routes without governance keys are behavior-identical to current runtime.
2. Existing `pluginBindings` and custom filters remain unchanged.
3. Governance filter should be fail-open for parse/runtime internal errors unless explicitly configured otherwise in later phases.
4. Static upstream route remains supported; governance still can gate it if configured.

## 7. Testing Strategy

## 7.1 Unit tests (bootstrap/governance package)

1. Config parser:
   - empty map => all disabled
   - partial config => only configured capability enabled
2. Rate limiter:
   - reject when tokens exhausted
3. Circuit breaker:
   - open/half-open/close transitions
4. Retry+timeout:
   - retries on timeout/5xx as configured
   - stops after max attempts
5. Degrade:
   - fallback response emitted on governance rejection.

## 7.2 Integration tests (`DefaultGatewayRequestHandlerTest`)

1. No governance config => request behavior unchanged.
2. Rate limit enabled => subsequent requests 429/degrade.
3. Timeout+retry enabled => retries occur and final status matches policy.
4. Circuit open => fast reject before proxy invocation.

## 8. Phase Delivery (this iteration)

1. Phase G1:
   - governance API model + parser + filter wiring
2. Phase G2:
   - local in-memory engine for 5 capabilities
3. Phase G3:
   - tests and verification commands

Out of scope now:

1. Distributed state store
2. Advanced adaptive algorithms
3. Performance benchmark tuning (deferred as requested)
