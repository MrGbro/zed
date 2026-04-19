package io.homeey.gateway.observe.otel;

import io.homeey.gateway.observe.api.ObserveOptions;
import io.homeey.gateway.observe.api.ObserveProvider;
import io.homeey.gateway.observe.api.ObserveProviderFactory;

public final class OtelObserveProviderFactory implements ObserveProviderFactory {
    @Override
    public ObserveProvider create(ObserveOptions options) {
        return new OtelObserveProvider(options == null ? ObserveOptions.defaults() : options);
    }
}
