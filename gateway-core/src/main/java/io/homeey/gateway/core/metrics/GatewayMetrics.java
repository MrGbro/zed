package io.homeey.gateway.core.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GatewayMetrics {
    private final ConcurrentHashMap<String, Long> counters = new ConcurrentHashMap<>();

    public GatewayMetrics() {
        counters.put("qps", 0L);
        counters.put("p99", 0L);
        counters.put("4xx", 0L);
        counters.put("5xx", 0L);
        counters.put("timeout", 0L);
        counters.put("retry", 0L);
    }

    public void markQps() {
        inc("qps");
    }

    public void mark4xx() {
        inc("4xx");
    }

    public void mark5xx() {
        inc("5xx");
    }

    public void markTimeout() {
        inc("timeout");
    }

    public void markRetry() {
        inc("retry");
    }

    public void recordP99(long p99) {
        counters.put("p99", p99);
    }

    public Map<String, Long> snapshot() {
        return Map.copyOf(counters);
    }

    private void inc(String key) {
        counters.compute(key, (k, v) -> v + 1);
    }
}
