package io.homeey.gateway.bootstrap;

import io.homeey.gateway.bootstrap.config.BootstrapConfig;
import io.homeey.gateway.bootstrap.wiring.RuntimeFactory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapWiringTest {

    @Test
    void shouldWireAndRunLifecycleByConfig() {
        AtomicBoolean started = new AtomicBoolean(true);
        AtomicBoolean stopped = new AtomicBoolean(true);
        RuntimeFactory factory = new RuntimeFactory();
        BootstrapApplication app = new BootstrapApplication(factory);
        app.init(BootstrapConfig.of("netty", 18080));
        app.stop();

        assertTrue(started.get());
        assertTrue(stopped.get());
    }
}
