package io.homeey.gateway.observe.api;

import io.homeey.gateway.transport.api.HttpRequestMessage;

/**
 * Safe fallback observe provider.
 */
public final class NoopObserveProvider implements ObserveProvider {
    @Override
    public RequestObservation begin(HttpRequestMessage request) {
        return RequestObservation.noop(request);
    }
}
