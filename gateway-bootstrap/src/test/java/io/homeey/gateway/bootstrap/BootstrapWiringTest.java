package io.homeey.gateway.bootstrap;

import io.homeey.gateway.bootstrap.config.BootstrapConfig;
import io.homeey.gateway.bootstrap.wiring.RuntimeFactory;
import io.homeey.gateway.transport.api.TransportServer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapWiringTest {

    @Test
    void shouldWireAndRunLifecycleByConfig() {
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean stopped = new AtomicBoolean(false);

        RuntimeFactory factory = new RuntimeFactory(port -> new TransportServer() {
            @Override
            public CompletableFuture<Void> start() {
                started.set(true);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Void> stop() {
                stopped.set(true);
                return CompletableFuture.completedFuture(null);
            }
        });

        BootstrapApplication app = new BootstrapApplication(factory);
        app.run(BootstrapConfig.of("netty", 18080));
        app.shutdown();

        assertTrue(started.get());
        assertTrue(stopped.get());
    }
}
