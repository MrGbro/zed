# 核心架构
```text
                Gateway Filter Chain
                         │
               Traffic Governance Filter
                         │
        ┌────────────────┼────────────────┐
        │                │                │
   RateLimiter     CircuitBreaker     RetryPolicy
        │                │                │
        └──────────────┬─┴───────────────┘
                       ↓
              Policy Engine（核心）
                       ↓
              State Store（状态管理）
                       ↓
              Scheduler / Timer（超时）
```
将RateLimiter，CircuitBreaker，RetryPolicy 都做成不同的扩展点，方便多实现，如限流可以用单机限流(sentinel,guava ratelimit)，也可以引入分布式限流.其余页类似。  
所以需要定义好清晰的治理Api