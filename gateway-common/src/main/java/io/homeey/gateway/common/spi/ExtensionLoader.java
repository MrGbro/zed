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

/**
 * SPI扩展加载器，负责加载和管理SPI扩展实现。
 * <p>
 * 该加载器从 {@code META-INF/gateway/} 目录下的配置文件中读取扩展定义，
 * 支持按需加载、默认扩展获取和基于条件的自动激活。
 * </p>
 * <p>
 * 配置文件格式：{@code 扩展名称=完整类名}
 * </p>
 *
 * @param <T> SPI接口类型
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
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

    /**
     * 获取指定SPI类型的扩展加载器。
     * <p>
     * 每个SPI类型只会有一个加载器实例，确保扩展的缓存和复用。
     * </p>
     *
     * @param type SPI接口类型
     * @param <T>  SPI接口类型
     * @return 扩展加载器实例
     * @throws IllegalArgumentException 如果type不是接口类型
     * @throws NullPointerException     如果type为null
     */
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (!type.isInterface()) {
            throw new IllegalArgumentException("SPI type must be interface: " + type.getName());
        }
        return (ExtensionLoader<T>) LOADERS.computeIfAbsent(type, ExtensionLoader::new);
    }

    /**
     * 获取指定名称的扩展实例。
     * <p>
     * 扩展实例会被缓存，多次调用返回同一个实例（单例模式）。
     * </p>
     *
     * @param name 扩展名称
     * @return 扩展实例
     * @throws IllegalArgumentException 如果扩展名称不存在
     */
    public T getExtension(String name) {
        String normalized = normalizeName(name);
        if (!extensionClasses.containsKey(normalized)) {
            throw new IllegalArgumentException("No such extension '" + normalized + "' for " + type.getName());
        }
        return instances.computeIfAbsent(normalized, key -> createInstance(extensionClasses.get(key)));
    }

    /**
     * 获取默认扩展实例。
     * <p>
     * 默认扩展由SPI接口上的 {@link SPI} 注解的value属性指定。
     * </p>
     *
     * @return 默认扩展实例
     * @throws IllegalStateException 如果SPI接口没有指定默认扩展
     */
    public T getDefaultExtension() {
        SPI spi = type.getAnnotation(SPI.class);
        if (spi == null || spi.value().isBlank()) {
            throw new IllegalStateException("No @SPI default for " + type.getName());
        }
        return getExtension(spi.value());
    }

    /**
     * 获取所有激活的扩展实例列表。
     * <p>
     * 根据分组和条件解析器，筛选出符合条件的扩展，并按order排序后返回。
     * </p>
     *
     * @param group              分组名称，用于匹配扩展的激活分组
     * @param conditionResolver  条件解析器，用于解析条件键的值
     * @return 激活的扩展实例列表
     */
    public List<T> getActivateExtensions(String group, Function<String, String> conditionResolver) {
        List<ActivateEntry<T>> entries = getActivateEntries(group, conditionResolver);
        List<T> result = new ArrayList<>(entries.size());
        for (ActivateEntry<T> entry : entries) {
            result.add(entry.instance());
        }
        return List.copyOf(result);
    }

    /**
     * 获取所有激活的扩展条目列表，包含名称、顺序和实例信息。
     * <p>
     * 与 {@link #getActivateExtensions(String, Function)} 类似，但返回的信息更详细。
     * </p>
     *
     * @param group              分组名称，用于匹配扩展的激活分组
     * @param conditionResolver  条件解析器，用于解析条件键的值
     * @return 激活的扩展条目列表
     */
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

    /**
     * 获取所有支持的扩展名称列表。
     *
     * @return 扩展名称列表
     */
    public List<String> getSupportedExtensions() {
        return List.copyOf(extensionClasses.keySet());
    }

    /**
     * 激活条目记录，包含扩展的名称、顺序和实例。
     *
     * @param name     扩展名称
     * @param order    激活顺序
     * @param instance 扩展实例
     * @param <T>      SPI接口类型
     */
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
