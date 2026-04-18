package io.homeey.gateway.common.spi;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class ExtensionLoader<T> {
    private static final String DIRECTORY = "META-INF/gateway/";
    private static final Map<Class<?>, ExtensionLoader<?>> LOADERS = new ConcurrentHashMap<>();

    private final Class<T> type;
    private final ClassLoader classLoader;
    private final Map<String, Class<? extends T>> extensionClasses;
    private final Map<String, T> instances = new ConcurrentHashMap<>();

    private ExtensionLoader(Class<T> type) {
        this.type = type;
        this.classLoader = resolveClassLoader();
        this.extensionClasses = loadExtensionClasses();
    }

    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (!type.isInterface()) {
            throw new IllegalArgumentException("SPI type must be interface: " + type.getName());
        }
        return (ExtensionLoader<T>) LOADERS.computeIfAbsent(type, ExtensionLoader::new);
    }

    public T getExtension(String name) {
        String normalized = normalizeName(name);
        if (!extensionClasses.containsKey(normalized)) {
            throw new IllegalArgumentException("No such extension '" + normalized + "' for " + type.getName());
        }
        return instances.computeIfAbsent(normalized, key -> createInstance(extensionClasses.get(key)));
    }

    public T getDefaultExtension() {
        SPI spi = type.getAnnotation(SPI.class);
        if (spi == null || spi.value().isBlank()) {
            throw new IllegalStateException("No @SPI default for " + type.getName());
        }
        return getExtension(spi.value());
    }

    public List<T> getActivateExtensions(String group, Function<String, String> conditionResolver) {
        List<ActivateEntry<T>> entries = getActivateEntries(group, conditionResolver);
        List<T> result = new ArrayList<>(entries.size());
        for (ActivateEntry<T> entry : entries) {
            result.add(entry.instance());
        }
        return List.copyOf(result);
    }

    public List<ActivateEntry<T>> getActivateEntries(String group, Function<String, String> conditionResolver) {
        List<ActivateCandidate<T>> candidates = new ArrayList<>();
        for (Map.Entry<String, Class<? extends T>> entry : extensionClasses.entrySet()) {
            Activate activate = entry.getValue().getAnnotation(Activate.class);
            if (activate == null) {
                continue;
            }
            if (!matchGroup(activate.group(), group)) {
                continue;
            }
            if (!matchActivation(activate, conditionResolver)) {
                continue;
            }
            T instance = getExtension(entry.getKey());
            candidates.add(new ActivateCandidate<>(activate.order(), entry.getKey(), instance));
        }
        candidates.sort(Comparator
                .comparingInt((ActivateCandidate<T> it) -> it.order)
                .thenComparing(it -> it.name));
        List<ActivateEntry<T>> result = new ArrayList<>(candidates.size());
        for (ActivateCandidate<T> candidate : candidates) {
            result.add(new ActivateEntry<>(candidate.name, candidate.order, candidate.instance));
        }
        return List.copyOf(result);
    }

    public List<String> getSupportedExtensions() {
        return List.copyOf(extensionClasses.keySet());
    }

    public record ActivateEntry<T>(
            String name,
            int order,
            T instance
    ) {
    }

    private static final class ActivateCandidate<T> {
        private final int order;
        private final String name;
        private final T instance;

        private ActivateCandidate(int order, String name, T instance) {
            this.order = order;
            this.name = name;
            this.instance = instance;
        }
    }

    private boolean matchGroup(String[] groups, String group) {
        if (groups == null || groups.length == 0) {
            return true;
        }
        if (group == null || group.isBlank()) {
            return false;
        }
        for (String candidate : groups) {
            if (group.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchActivation(Activate activate, Function<String, String> conditionResolver) {
        if (!matchConditionKeys(activate.conditionKeys(), conditionResolver, activate.matchAll())) {
            return false;
        }
        return matchExpressions(activate.conditions(), conditionResolver, activate.matchAll());
    }

    private boolean matchConditionKeys(String[] keys, Function<String, String> resolver, boolean matchAll) {
        if (keys == null || keys.length == 0) {
            return true;
        }
        if (resolver == null) {
            return false;
        }
        int matched = 0;
        for (String key : keys) {
            String value = resolver.apply(key);
            boolean ok = value != null;
            if (matchAll && !ok) {
                return false;
            }
            if (!matchAll && ok) {
                return true;
            }
            if (ok) {
                matched++;
            }
        }
        if (matchAll) {
            return true;
        }
        return matched > 0;
    }

    private boolean matchExpressions(String[] expressions, Function<String, String> resolver, boolean matchAll) {
        if (expressions == null || expressions.length == 0) {
            return true;
        }
        if (resolver == null) {
            return false;
        }
        int matched = 0;
        for (String raw : expressions) {
            String expression = raw == null ? "" : raw.trim();
            if (expression.isEmpty()) {
                continue;
            }
            boolean ok = evaluateExpression(expression, resolver);
            if (matchAll && !ok) {
                return false;
            }
            if (!matchAll && ok) {
                return true;
            }
            if (ok) {
                matched++;
            }
        }
        if (matchAll) {
            return true;
        }
        return matched > 0;
    }

    private boolean evaluateExpression(String expression, Function<String, String> resolver) {
        int neqIndex = expression.indexOf("!=");
        if (neqIndex > 0) {
            String key = expression.substring(0, neqIndex).trim();
            String expected = expression.substring(neqIndex + 2).trim();
            String actual = resolver.apply(key);
            if (actual == null) {
                return true;
            }
            return !actual.equals(expected);
        }
        int eqIndex = expression.indexOf('=');
        if (eqIndex > 0) {
            String key = expression.substring(0, eqIndex).trim();
            String expected = expression.substring(eqIndex + 1).trim();
            String actual = resolver.apply(key);
            return actual != null && actual.equals(expected);
        }
        if (expression.startsWith("!")) {
            String key = expression.substring(1).trim();
            String actual = resolver.apply(key);
            return actual == null || actual.isBlank() || "false".equalsIgnoreCase(actual);
        }
        String actual = resolver.apply(expression);
        return actual != null && !actual.isBlank() && !"false".equalsIgnoreCase(actual);
    }

    private T createInstance(Class<? extends T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to instantiate extension " + clazz.getName(), e);
        }
    }

    private Map<String, Class<? extends T>> loadExtensionClasses() {
        String fileName = DIRECTORY + type.getName();
        Enumeration<URL> urls;
        try {
            urls = classLoader.getResources(fileName);
        } catch (Exception e) {
            throw new ServiceConfigurationError("Failed loading extension resources for " + type.getName(), e);
        }
        if (urls == null || !urls.hasMoreElements()) {
            return Map.of();
        }
        Map<String, Class<? extends T>> classes = new LinkedHashMap<>();
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            try (InputStream stream = url.openStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                int lineNo = 0;
                while ((line = reader.readLine()) != null) {
                    lineNo++;
                    String content = stripComment(line).trim();
                    if (content.isEmpty()) {
                        continue;
                    }
                    int idx = content.indexOf('=');
                    if (idx < 1 || idx == content.length() - 1) {
                        throw new ServiceConfigurationError("Invalid extension line in " + fileName + ":" + lineNo);
                    }
                    String name = normalizeName(content.substring(0, idx));
                    String className = content.substring(idx + 1).trim();
                    Class<?> loaded = Class.forName(className, true, classLoader);
                    if (!type.isAssignableFrom(loaded)) {
                        throw new ServiceConfigurationError(className + " is not subtype of " + type.getName());
                    }
                    classes.put(name, (Class<? extends T>) loaded);
                }
            } catch (ServiceConfigurationError error) {
                throw error;
            } catch (Exception e) {
                throw new ServiceConfigurationError("Failed loading extensions for " + type.getName(), e);
            }
        }
        return Map.copyOf(classes);
    }

    private String stripComment(String line) {
        int idx = line.indexOf('#');
        if (idx < 0) {
            return line;
        }
        return line.substring(0, idx);
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Extension name cannot be blank for " + type.getName());
        }
        return name.trim();
    }

    private ClassLoader resolveClassLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            return context;
        }
        return ExtensionLoader.class.getClassLoader();
    }
}
