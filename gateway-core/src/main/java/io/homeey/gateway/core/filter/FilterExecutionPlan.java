package io.homeey.gateway.core.filter;

import io.homeey.gateway.plugin.api.FilterInvocation;

import java.util.List;

/**
 * 过滤器执行计划，封装过滤器的执行顺序和配置。
 * <p>
 * 该计划包含一组按顺序排列的过滤器调用，用于在请求处理过程中执行。
 * </p>
 *
 * @param invocations 过滤器调用列表
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public record FilterExecutionPlan(List<FilterInvocation> invocations) {

    /**
     * 紧凑构造函数，确保invocations不可变。
     *
     * @param invocations 过滤器调用列表
     */
    public FilterExecutionPlan {
        invocations = List.copyOf(invocations);
    }
}
