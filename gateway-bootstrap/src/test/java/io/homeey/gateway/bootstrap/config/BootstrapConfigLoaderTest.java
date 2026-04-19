package io.homeey.gateway.bootstrap.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BootstrapConfigLoaderTest {
    private static final String CONFIG_PROPERTY = "gateway.bootstrap.config";
    private String originalConfigProperty;

    @BeforeEach
    void setUp() {
        originalConfigProperty = System.getProperty(CONFIG_PROPERTY);
        System.clearProperty(CONFIG_PROPERTY);
    }

    @AfterEach
    void tearDown() {
        if (originalConfigProperty == null) {
            System.clearProperty(CONFIG_PROPERTY);
        } else {
            System.setProperty(CONFIG_PROPERTY, originalConfigProperty);
        }
    }

    @Test
    void shouldLoadFromClasspathWhenExternalPathMissing() {
        BootstrapConfig config = BootstrapConfigLoader.load();

        assertEquals("netty", config.transportType());
        assertEquals("nacos", config.configProviderType());
        assertEquals("nacos", config.discoveryProviderType());
        assertEquals("async-http-client", config.proxyClientType());
        assertEquals(19080, config.port());
        assertEquals("127.0.0.1:8848", config.nacosServerAddr());
        assertEquals("classpath.routes.json", config.routesDataId());
        assertEquals("CLASSPATH_GROUP", config.group());
        assertEquals(6000L, config.gracefulTimeoutMillis());
        assertEquals("static", config.staticResourcesDir());
        assertEquals("otel", config.observeProviderType());
        assertEquals("", config.otlpEndpoint());
        assertEquals("gateway-test-node", config.observeServiceName());
        assertEquals(5000L, config.observeExportIntervalMillis());
        assertEquals("/metrics", config.metricsPath());
        assertEquals(true, config.accessLogEnabled());
    }

    @Test
    void shouldPreferExternalYamlWhenPropertySpecified() throws IOException {
        Path file = Files.createTempFile("bootstrap-external-", ".yaml");
        Files.writeString(file, """
                transportType: external-netty
                configProviderType: external-config
                discoveryProviderType: external-discovery
                proxyClientType: external-proxy
                port: 29080
                nacosServerAddr: 10.0.0.1:8848
                routesDataId: external.routes.json
                group: EXTERNAL_GROUP
                gracefulTimeoutMillis: 9000
                staticResourcesDir: ./tmp/static-assets
                observeProviderType: custom-observe
                otlpEndpoint: http://collector:4317
                otlpHeaders:
                  Authorization: Bearer test-token
                observeServiceName: gateway-ext
                observeExportIntervalMillis: 15000
                metricsPath: /actuator/metrics
                accessLogEnabled: false
                """, StandardCharsets.UTF_8);
        System.setProperty(CONFIG_PROPERTY, file.toString());

        BootstrapConfig config = BootstrapConfigLoader.load();

        assertEquals("external-netty", config.transportType());
        assertEquals("external-config", config.configProviderType());
        assertEquals("external-discovery", config.discoveryProviderType());
        assertEquals("external-proxy", config.proxyClientType());
        assertEquals(29080, config.port());
        assertEquals("10.0.0.1:8848", config.nacosServerAddr());
        assertEquals("external.routes.json", config.routesDataId());
        assertEquals("EXTERNAL_GROUP", config.group());
        assertEquals(9000L, config.gracefulTimeoutMillis());
        assertEquals("./tmp/static-assets", config.staticResourcesDir());
        assertEquals("custom-observe", config.observeProviderType());
        assertEquals("http://collector:4317", config.otlpEndpoint());
        assertEquals("Bearer test-token", config.otlpHeaders().get("Authorization"));
        assertEquals("gateway-ext", config.observeServiceName());
        assertEquals(15000L, config.observeExportIntervalMillis());
        assertEquals("/actuator/metrics", config.metricsPath());
        assertEquals(false, config.accessLogEnabled());
    }

    @Test
    void shouldFallbackToDefaultsWhenYamlFieldsMissing() throws IOException {
        Path file = Files.createTempFile("bootstrap-minimal-", ".yaml");
        Files.writeString(file, """
                port: 20001
                group: MINI_GROUP
                """, StandardCharsets.UTF_8);
        System.setProperty(CONFIG_PROPERTY, file.toString());

        BootstrapConfig config = BootstrapConfigLoader.load();

        BootstrapConfig defaults = BootstrapConfig.defaultConfig();
        assertEquals(defaults.transportType(), config.transportType());
        assertEquals(defaults.configProviderType(), config.configProviderType());
        assertEquals(defaults.discoveryProviderType(), config.discoveryProviderType());
        assertEquals(defaults.proxyClientType(), config.proxyClientType());
        assertEquals(20001, config.port());
        assertEquals(defaults.nacosServerAddr(), config.nacosServerAddr());
        assertEquals(defaults.routesDataId(), config.routesDataId());
        assertEquals("MINI_GROUP", config.group());
        assertEquals(defaults.gracefulTimeoutMillis(), config.gracefulTimeoutMillis());
        assertEquals(defaults.staticResourcesDir(), config.staticResourcesDir());
        assertEquals(defaults.observeProviderType(), config.observeProviderType());
        assertEquals(defaults.otlpEndpoint(), config.otlpEndpoint());
        assertEquals(defaults.otlpHeaders(), config.otlpHeaders());
        assertEquals(defaults.observeServiceName(), config.observeServiceName());
        assertEquals(defaults.observeExportIntervalMillis(), config.observeExportIntervalMillis());
        assertEquals(defaults.metricsPath(), config.metricsPath());
        assertEquals(defaults.accessLogEnabled(), config.accessLogEnabled());
    }

    @Test
    void shouldLoadDefaultConfigWhenClasspathYamlMissing() {
        BootstrapConfig config = BootstrapConfigLoader.load("missing-bootstrap-config.yaml");
        BootstrapConfig defaults = BootstrapConfig.defaultConfig();

        assertEquals(defaults.transportType(), config.transportType());
        assertEquals(defaults.configProviderType(), config.configProviderType());
        assertEquals(defaults.discoveryProviderType(), config.discoveryProviderType());
        assertEquals(defaults.proxyClientType(), config.proxyClientType());
        assertEquals(defaults.port(), config.port());
        assertEquals(defaults.nacosServerAddr(), config.nacosServerAddr());
        assertEquals(defaults.routesDataId(), config.routesDataId());
        assertEquals(defaults.group(), config.group());
        assertEquals(defaults.gracefulTimeoutMillis(), config.gracefulTimeoutMillis());
        assertEquals(defaults.staticResourcesDir(), config.staticResourcesDir());
        assertEquals(defaults.observeProviderType(), config.observeProviderType());
        assertEquals(defaults.otlpEndpoint(), config.otlpEndpoint());
        assertEquals(defaults.otlpHeaders(), config.otlpHeaders());
        assertEquals(defaults.observeServiceName(), config.observeServiceName());
        assertEquals(defaults.observeExportIntervalMillis(), config.observeExportIntervalMillis());
        assertEquals(defaults.metricsPath(), config.metricsPath());
        assertEquals(defaults.accessLogEnabled(), config.accessLogEnabled());
    }
}
