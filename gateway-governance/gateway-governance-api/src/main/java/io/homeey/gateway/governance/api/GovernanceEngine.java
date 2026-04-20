package io.homeey.gateway.governance.api;

import io.homeey.gateway.common.spi.SPI;
import io.homeey.gateway.transport.api.HttpResponseMessage;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

@SPI("local")
public interface GovernanceEngine {
    CompletionStage<HttpResponseMessage> execute(
            GovernanceExecutionContext context,
            Supplier<CompletionStage<HttpResponseMessage>> upstreamCall
    );

    static boolean enabled(Map<String, Object> entries) {
        if (entries == null) {
            return false;
        }
        Object value = entries.get("governance.enabled");
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
