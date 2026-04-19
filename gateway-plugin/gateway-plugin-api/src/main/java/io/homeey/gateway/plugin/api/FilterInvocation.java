package io.homeey.gateway.plugin.api;

import java.util.Objects;

/**
 * 过滤器调用记录，封装单个过滤器的执行信息。
 * <p>
 * 该记录包含过滤器名称、过滤器实例和失败策略，用于构建过滤器执行计划。
 * </p>
 *
 * @param name       过滤器名称，用于标识和日志
 * @param filter     过滤器实例，执行具体的过滤逻辑
 * @param failPolicy 失败策略，定义过滤器执行失败时的处理方式
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public record FilterInvocation(
        String name,
        GatewayFilter filter,
        FilterFailPolicy failPolicy
) {
    /**
     * 紧凑构造函数，验证所有参数不为null。
     *
     * @param name       过滤器名称，不能为null
     * @param filter     过滤器实例，不能为null
     * @param failPolicy 失败策略，不能为null
     * @throws NullPointerException 如果任何参数为null
     */
    public FilterInvocation {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(failPolicy, "failPolicy");
    }
}
