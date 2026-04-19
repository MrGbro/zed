package io.homeey.gateway.bootstrap.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class BootstrapConfigLoader {
    private static final String CONFIG_PROPERTY = "gateway.bootstrap.config";
    private static final String DEFAULT_CLASSPATH_CONFIG = "bootstrap.yaml";
    private static final Yaml YAML = new Yaml(new SafeConstructor(new LoaderOptions()));

    private BootstrapConfigLoader() {
    }

    public static BootstrapConfig load() {
        return load(DEFAULT_CLASSPATH_CONFIG);
    }

    static BootstrapConfig load(String classpathResource) {
        String externalPath = System.getProperty(CONFIG_PROPERTY);
        if (externalPath != null && !externalPath.isBlank()) {
            return loadFromPath(Path.of(externalPath));
        }
        return loadFromClasspathOrDefault(classpathResource);
    }

    private static BootstrapConfig loadFromPath(Path path) {
        try (InputStream stream = Files.newInputStream(path)) {
            return mergeWithDefaults(parseYaml(stream));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load bootstrap config from path: " + path, e);
        }
    }

    private static BootstrapConfig loadFromClasspathOrDefault(String classpathResource) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(classpathResource)) {
            if (stream == null) {
                return BootstrapConfig.defaultConfig();
            }
            return mergeWithDefaults(parseYaml(stream));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load bootstrap config from classpath: " + classpathResource, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseYaml(InputStream stream) {
        Object value = YAML.load(stream);
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("bootstrap yaml must be a key-value map");
        }
        return (Map<String, Object>) map;
    }

    private static BootstrapConfig mergeWithDefaults(Map<String, Object> raw) {
        BootstrapConfig defaults = BootstrapConfig.defaultConfig();
        return new BootstrapConfig(
                stringValue(raw, "transportType", defaults.transportType()),
                stringValue(raw, "configProviderType", defaults.configProviderType()),
                stringValue(raw, "discoveryProviderType", defaults.discoveryProviderType()),
                stringValue(raw, "proxyClientType", defaults.proxyClientType()),
                intValue(raw, "port", defaults.port()),
                stringValue(raw, "nacosServerAddr", defaults.nacosServerAddr()),
                stringValue(raw, "routesDataId", defaults.routesDataId()),
                stringValue(raw, "group", defaults.group()),
                longValue(raw, "gracefulTimeoutMillis", defaults.gracefulTimeoutMillis()),
                stringValue(raw, "staticResourcesDir", defaults.staticResourcesDir())
        );
    }

    private static String stringValue(Map<String, Object> raw, String key, String fallback) {
        Object value = raw.get(key);
        if (value == null) {
            return fallback;
        }
        String text = value.toString();
        return text.isBlank() ? fallback : text;
    }

    private static int intValue(Map<String, Object> raw, String key, int fallback) {
        Object value = raw.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private static long longValue(Map<String, Object> raw, String key, long fallback) {
        Object value = raw.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
