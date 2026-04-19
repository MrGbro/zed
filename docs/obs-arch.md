# arch
```text
                   ┌──────────────────────┐
                   │   Gateway Core       │
                   │  (Netty / Reactor)   │
                   └─────────┬────────────┘
                             │
                    Filter Chain（关键）
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
  Metrics Filter      Trace Filter        Log Filter
        │                    │                    │
        └──────────────┬─────┴──────────────┬─────┘
                       ↓                    ↓
               Observability SPI（核心抽象层）
                       ↓
             OpenTelemetry Bridge（适配层）
                       ↓
             :contentReference[oaicite:0]{index=0} SDK
                       ↓
              OTLP Exporter（统一出口）
                       ↓
   ┌───────────────┬───────────────┬───────────────┐
   ↓               ↓               ↓
Prometheus     Jaeger          Loki
(metrics)      (trace)         (logs)
```