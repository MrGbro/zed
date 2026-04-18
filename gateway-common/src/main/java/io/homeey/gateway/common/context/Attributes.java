package io.homeey.gateway.common.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 属性容器，用于在网关请求上下文中存储和传递自定义属性。
 * <p>
 * 该容器使用 {@link ConcurrentHashMap} 实现线程安全的属性存储，
 * 支持在过滤器链、处理器等组件之间共享数据。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @since  2026/04/18
 */
public final class Attributes {
    private final Map<String, Object> values = new ConcurrentHashMap<>();

    public void put(String key, Object value) {
        values.put(key, value);
    }

    public Object get(String key) {
        return values.get(key);
    }

    public boolean containsKey(String key) {
        return values.containsKey(key);
    }
}
