package io.homeey.gateway.core;

import io.homeey.gateway.common.error.ErrorCategory;
import io.homeey.gateway.core.error.ErrorMapper;
import io.homeey.gateway.core.metrics.GatewayMetrics;
import io.homeey.gateway.core.tracing.TraceContextFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorMapperTest {

    @Test
    void shouldMapClientAndSystemErrorsAndInjectTraceHeader() {
        TraceContextFactory traceFactory = new TraceContextFactory();
        String traceId = traceFactory.newTraceId();

        ErrorMapper mapper = new ErrorMapper();
        Map<String, Object> client = mapper.map(
                new IllegalArgumentException("bad request"),
                traceId
        );
        Map<String, Object> system = mapper.map(
                new IllegalStateException("internal"),
                traceId
        );

        assertEquals("GW4001", client.get("code"));
        assertEquals(ErrorCategory.CLIENT_ERROR.name(), client.get("category"));
        assertEquals(400, client.get("status"));
        assertEquals(traceId, ((Map<?, ?>) client.get("headers")).get("X-Trace-Id"));

        assertEquals("GW5001", system.get("code"));
        assertEquals(ErrorCategory.SYSTEM_ERROR.name(), system.get("category"));
        assertEquals(500, system.get("status"));
        assertEquals(traceId, ((Map<?, ?>) system.get("headers")).get("X-Trace-Id"));
    }

    @Test
    void shouldRecordMetricsCounters() {
        GatewayMetrics metrics = new GatewayMetrics();
        metrics.markQps();
        metrics.mark4xx();
        metrics.mark5xx();
        metrics.markTimeout();
        metrics.markRetry();
        metrics.recordP99(12L);

        Map<String, Long> snapshot = metrics.snapshot();
        assertEquals(1L, snapshot.get("qps"));
        assertEquals(1L, snapshot.get("4xx"));
        assertEquals(1L, snapshot.get("5xx"));
        assertEquals(1L, snapshot.get("timeout"));
        assertEquals(1L, snapshot.get("retry"));
        assertEquals(12L, snapshot.get("p99"));
        assertTrue(snapshot.containsKey("p99"));
    }
}
