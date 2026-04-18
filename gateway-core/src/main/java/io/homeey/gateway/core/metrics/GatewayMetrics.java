package io.homeey.gateway.core.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关指标收集器，收集和统计网关运行时的关键指标。
 * <p>
 * 支持的指标包括：QPS、P99延迟、4xx/5xx错误数、超时次数和重试次数。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
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
