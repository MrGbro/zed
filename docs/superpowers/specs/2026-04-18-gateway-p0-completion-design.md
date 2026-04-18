# Gateway P0 Completion Design (Phase1-2 Gap Closure)

- Date: 2026-04-18
- Scope: Complete all `P0` items from `docs/phase1-phase2-completion-gap-2026-04-18.md`
- Constraints:
  - Do not use sub-agent mode
  - Nacos address: `192.168.79.144:8848`
  - Nacos auth: disabled
  - Fallback policy: auto fallback to local in-memory config/discovery when Nacos is unavailable
  - Default proxy client: `async-http-client`

## 1. Goals

This design closes five P0 gaps:

1. Real data-plane forwarding pipeline (replace demo `/ping` behavior)
2. HTTP/2 inbound support
3. Real Nacos integration
4. Admin publish -> config center -> node hot reload closed loop
5. Runtime lifecycle `init -> start -> stop` with graceful shutdown

## 2. Architecture and Module Boundaries

Keep existing module split; fill capabilities incrementally:

1. `gateway-admin`
- Keep route and publish APIs
- Persist published route snapshot to Nacos

2. `gateway-config/gateway-config-nacos` and `gateway-registry/gateway-registry-nacos`
- Integrate real Nacos SDK
- Add fallback wrapper: Nacos unavailable -> in-memory provider

3. `gateway-core`
- Maintain immutable runtime snapshots (route table, version, discovery view)
- Subscribe config changes and atomically switch snapshot on successful rebuild

4. `gateway-transport/gateway-transport-netty`
- Implement real reverse proxy path
- Support HTTP/1.1 + HTTP/2 inbound

5. `gateway-bootstrap`
- Enforce explicit lifecycle:
  - `init`: wiring/providers/snapshot preload/subscriptions
  - `start`: start transport and accept traffic
  - `stop`: graceful drain and full resource shutdown

6. `gateway-proxy` (new aggregate)
- `gateway-proxy-api` defines `ProxyClient` contract and request/response model
- `gateway-proxy-async-http` provides default implementation
- `gateway-proxy-okhttp` provides alternative implementation
- `gateway-proxy-reactor-netty` provides optional implementation

## 3. Config Model and Publish/Reload Flow

## 3.1 Nacos keys

- `serverAddr`: `192.168.79.144:8848`
- `group`: `GATEWAY`
- `dataId` (routes): `gateway.routes.json`
- `dataId` (optional discovery override): `gateway.discovery.json`

## 3.2 Route snapshot payload

```json
{
  "version": "v1713430000000",
  "publishedAt": "2026-04-18T12:00:00Z",
  "routes": [
    {
      "id": "r1",
      "host": "api.example.com",
      "pathPrefix": "/orders",
      "method": "GET",
      "upstreamService": "order-service",
      "upstreamPath": "/orders"
    }
  ]
}
```

## 3.3 Closed-loop flow

1. Admin validates route draft and generates `version = v{epochMillis}`
2. Admin publishes full snapshot JSON to Nacos (`gateway.routes.json`, `GATEWAY`)
3. Gateway node receives config change via `ConfigProvider.subscribe(...)`
4. Runtime rebuilds snapshot in background
5. On success, snapshot reference is atomically switched
6. On failure, old snapshot remains active and failure metrics are incremented
7. If Nacos fails, provider falls back to in-memory mode and runtime keeps serving

## 4. Data Plane and Proxy Client Strategy

## 4.1 Inbound

- Netty server accepts HTTP/1.1 and HTTP/2 (h2c baseline)
- Request adapted into internal normalized request model:
  - method, host, path, query, headers, body

## 4.2 Route + instance resolution

1. Match route from runtime snapshot
2. Resolve upstream instances by `upstreamService` from discovery provider
3. Select instance via round-robin

## 4.3 Outbound with pluggable proxy client

Define framework-agnostic API:

- `ProxyClient`
- `ProxyRequest`
- `ProxyResponse`

Implementations:

1. `gateway-proxy-async-http` (default)
2. `gateway-proxy-reactor-netty`
3. `gateway-proxy-okhttp`

Wiring is configuration-driven in bootstrap, without exposing implementation types to core contracts.

## 4.4 Error semantics

- no route: `404`
- no instance: `503`
- upstream timeout: `504`
- upstream connect failure: `502`
- all responses include `X-Trace-Id`

## 5. Lifecycle and Graceful Shutdown

## 5.1 init

1. Load bootstrap config
2. Build providers (Nacos + fallback wrappers)
3. Load initial snapshot
4. Register config/discovery subscriptions

## 5.2 start

1. Start Netty transport
2. Mark readiness `UP`
3. Begin serving traffic

## 5.3 stop

1. Mark status `DRAINING`
2. Stop accepting new requests
3. Wait in-flight completion within configurable timeout
4. Close transport, proxy client, providers/listeners, and worker resources
5. Mark status `DOWN`

## 6. Testing Strategy

1. Unit:
- route match and snapshot switch correctness
- fallback behavior when Nacos calls fail
- proxy client selection by config

2. Integration:
- admin publish writes Nacos and gateway receives update
- real forwarding path to upstream test server
- HTTP/2 inbound request path

3. Smoke:
- existing `scripts/e2e/run-phase2-smoke.ps1` upgraded to verify live hot reload through Nacos

## 7. Rollout Plan

1. Add pluggable proxy-client API and default async-http-client implementation
   - implemented in dedicated `gateway-proxy` aggregate modules
2. Replace Netty demo handler with real route+forward pipeline and HTTP/2 inbound
3. Integrate Nacos SDK in config/discovery modules with automatic in-memory fallback
4. Connect admin publish to Nacos route snapshot data
5. Wire bootstrap lifecycle (`init/start/stop`) and graceful drain
6. Update completion checklist and docs

## 8. Out-of-Scope for This Round

1. Full resilience suite (retry budgets, circuit breaker state machine, advanced backpressure)
2. Full plugin marketplace modules
3. Production-grade multi-tenant governance
