package io.homeey.gateway.core.tracing;

import java.util.UUID;

public final class TraceContextFactory {

    public String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
