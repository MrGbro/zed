package io.homeey.gateway.bootstrap;

import io.homeey.gateway.bootstrap.config.BootstrapConfig;
import io.homeey.gateway.bootstrap.wiring.RuntimeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapWiringTest {
    private static final String CONFIG_PROPERTY = "gateway.bootstrap.config";

    @AfterEach
    void clearConfigProperty() {
        System.clearProperty(CONFIG_PROPERTY);
    }

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

    @Test
    void shouldRunLifecycleByYamlConfig() {
        AtomicBoolean started = new AtomicBoolean(true);
        AtomicBoolean stopped = new AtomicBoolean(true);
        RuntimeFactory factory = new RuntimeFactory();
        BootstrapApplication app = new BootstrapApplication(factory);
        app.init(io.homeey.gateway.bootstrap.config.BootstrapConfigLoader.load());
        app.stop();

        assertTrue(started.get());
        assertTrue(stopped.get());
    }
}
