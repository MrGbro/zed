# Gateway Observability Redesign (Based on `docs/obs-arch.md`)

- Date: 2026-04-19
- Baseline: `docs/obs-arch.md`
- Scope: Re-design observability architecture for Phase3/4 evolution

## 1. Design Goals

1. Follow `obs-arch` strictly: `Filter Chain -> Observability SPI -> OpenTelemetry Bridge -> OTel SDK -> OTLP Exporter -> Backends`.
2. Keep implementation pluggable: do not bind core/bootstrap to one concrete backend.
3. Provide default metrics path compatible with Prometheus ecosystem.
4. Split logs by purpose:
   - `application.log`: startup, lifecycle, SPI/plugin loading, config change, system events
   - `access.log`: request access logs only

## 2. Target Architecture

Runtime pipeline:

1. Gateway transport ingress (netty/vertx/reactor)
2. Filter chain with built-in observability stages:
   - `MetricsFilter`
   - `TraceFilter`
   - `LogFilter`
3. Observability SPI (core abstraction)
4. OpenTelemetry bridge implementation
5. OTel SDK (trace/metrics/logs)
6. OTLP exporter to Collector
7. Collector fan-out:
   - Prometheus (metrics)
   - Jaeger (trace)
   - Loki (logs)

## 3. Module Decomposition

## 3.1 New module family

1. `gateway-observe` (aggregator)
2. `gateway-observe/gateway-observe-api`
3. `gateway-observe/gateway-observe-otel` (default implementation)

## 3.2 Dependency direction

1. `gateway-core -> gateway-observe-api` only
2. `gateway-bootstrap -> gateway-observe-api + selected implementation`
3. `gateway-observe-otel -> gateway-observe-api + OpenTelemetry deps`
4. No `gateway-core -> gateway-observe-otel` dependency

## 4. SPI Contracts (`gateway-observe-api`)

## 4.1 Factory

1. `ObserveProviderFactory` annotated with `@SPI("otel")`
2. Create provider using observe config from bootstrap

## 4.2 Provider

`ObserveProvider` lifecycle:

1. `init()`
2. `start()`
3. `stop()`
4. `metricsSnapshot()` for pull-style `/metrics` output

## 4.3 Request observation bridge

`ObservationFacade` (or equivalent API) responsibilities:

1. begin request/span from inbound request
2. attach/extract `traceId` from headers
3. mark route match, upstream call, error category
4. record latency and status counters
5. emit structured access event payload

## 5. Built-in Observability Filters

Three built-in filters are always present in filter chain:

1. `TraceFilter`
   - Extract W3C trace context if present
   - Create server span if absent
   - Ensure response carries trace id (header)
2. `MetricsFilter`
   - Count requests by route/status/method
   - Record request duration histogram
   - Record timeout/upstream-error counters
3. `LogFilter`
   - Emit one structured access record per request
   - Include: `traceId, routeId, method, path, status, latencyMs, upstream, errorCategory`

Failure policy for all observability filters: fail-open (never break traffic).

## 6. Default OTel Implementation (`gateway-observe-otel`)

## 6.1 OTel SDK

1. Build tracer/meter/logger providers
2. Resource attributes:
   - `service.name=gateway-node`
   - `service.version`
   - `deployment.environment`

## 6.2 OTLP exporter

1. Trace exporter: OTLP gRPC/HTTP (configurable)
2. Metrics exporter: OTLP (periodic export)
3. Logs exporter: OTLP

## 6.3 Prometheus compatibility (default)

Not binding gateway runtime to Prometheus SDK directly in core.

Default implementation provides one of these two compatible outputs:

1. `metricsSnapshot()` endpoint in Prometheus text format at gateway `/metrics`
2. OTLP -> Collector -> Prometheus endpoint / remote_write (recommended deployment path)

## 7. Bootstrap Integration

## 7.1 Bootstrap config extensions

Add observe section (or flat fields) in `BootstrapConfig`:

1. `observeProviderType` (default `otel`)
2. `otlpEndpoint`
3. `otlpHeaders`
4. `observeServiceName`
5. `observeExportIntervalMillis`
6. `metricsPath` (default `/metrics`)
7. `accessLogEnabled` (default true)

## 7.2 RuntimeFactory wiring

1. Load `ObserveProviderFactory` via `ExtensionLoader`
2. Create/start observe provider during runtime init/start
3. Wrap request handler:
   - short-circuit `GET /metrics`
   - otherwise execute request with observation context

## 8. Logging Split Strategy

In data plane process (`gateway-bootstrap`) add logback config:

1. Logger `io.homeey.gateway.access` -> `access.log` only
2. Root and system loggers -> `application.log`
3. `additivity=false` on access logger to avoid duplication

Event routing rules:

1. startup/shutdown/SPI load/plugin lifecycle/config updates -> `application.log`
2. one-line structured request completion record -> `access.log`

## 9. Error Model and Non-Functional Rules

1. Observability must not change business response semantics.
2. Exporter failure must not fail request path.
3. Backpressure in exporter path must degrade by dropping telemetry, not request traffic.
4. `/metrics` endpoint must be cheap and non-blocking.

## 10. Verification and Acceptance

## 10.1 Unit tests

1. SPI loading for observe provider
2. Filter behavior:
   - trace context propagation
   - metric increments and histogram recording
   - access log field completeness

## 10.2 Integration tests

1. bootstrap wiring loads observe provider by config
2. `/metrics` reachable in data plane process
3. request through transport emits trace/metric/log artifacts (mock exporter or in-memory sink)

## 10.3 Log routing tests

1. startup logs land in `application.log`
2. access events land in `access.log`
3. no duplicated access lines in `application.log`

## 11. Phase Plan (After Redesign)

1. Phase3-Obs-M1:
   - module split + SPI + default OTel provider + `/metrics`
2. Phase3-Obs-M2:
   - full trace span taxonomy + structured access logs + collector deployment doc
3. Phase4-Obs-M3:
   - advanced labels/cardinality controls, sampling policies, multi-tenant routing

## 12. Compatibility

1. Existing gateway request handling remains unchanged in semantics.
2. Existing admin APIs remain unchanged.
3. Observability is additive and can be toggled by bootstrap config.
