package io.homeey.gateway.bootstrap;

import io.homeey.gateway.bootstrap.config.BootstrapConfig;
import io.homeey.gateway.bootstrap.wiring.RuntimeFactory;
import io.homeey.gateway.transport.api.TransportServer;

import java.util.Objects;

public final class BootstrapApplication {
    private final RuntimeFactory runtimeFactory;
    private TransportServer runningTransport;

    public BootstrapApplication(RuntimeFactory runtimeFactory) {
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "runtimeFactory");
    }

    public void run(BootstrapConfig config) {
        this.runningTransport = runtimeFactory.createTransport(config);
        this.runningTransport.start().toCompletableFuture().join();
    }

    public void shutdown() {
        if (runningTransport != null) {
            runningTransport.stop().toCompletableFuture().join();
        }
    }
}
