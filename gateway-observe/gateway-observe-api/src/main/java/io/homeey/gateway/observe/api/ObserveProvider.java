package io.homeey.gateway.observe.api;

import io.homeey.gateway.transport.api.HttpRequestMessage;

/**
 * Observability provider lifecycle and request bridge.
 */
public interface ObserveProvider {
    default void init() {
        // no-op
    }

    default void start() {
        // no-op
    }

    default void stop() {
        // no-op
    }

    default RequestObservation begin(HttpRequestMessage request) {
        return RequestObservation.noop(request);
    }

    default String metricsSnapshot() {
        return "";
    }
}
