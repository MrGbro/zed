package io.homeey.gateway.core.filter;

import io.homeey.gateway.common.spi.ExtensionLoader;
import io.homeey.gateway.plugin.api.FilterFailPolicy;
import io.homeey.gateway.plugin.api.FilterInvocation;
import io.homeey.gateway.plugin.api.GatewayFilter;
import io.homeey.gateway.plugin.api.PluginBinding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 过滤器执行计划编译器，根据路由配置和插件绑定生成执行计划。
 * <p>
 * 该编译器负责：
 * <ul>
 *   <li>加载全局和路由级别的自动激活过滤器</li>
 *   <li>根据插件绑定添加手动配置的过滤器</li>
 *   <li>按顺序组织过滤器调用链</li>
 * </ul>
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class FilterExecutionPlanCompiler {
    private static final String GROUP_GLOBAL = "global";
    private static final String GROUP_ROUTE = "route";

    private final ExtensionLoader<GatewayFilter> extensionLoader;

    public FilterExecutionPlanCompiler() {
        this(ExtensionLoader.getExtensionLoader(GatewayFilter.class));
    }

    public FilterExecutionPlanCompiler(ExtensionLoader<GatewayFilter> extensionLoader) {
        this.extensionLoader = Objects.requireNonNull(extensionLoader, "extensionLoader");
    }

    public FilterExecutionPlan compile(String routeId, List<PluginBinding> bindings, Map<String, Object> conditionBag) {
        List<FilterInvocation> invocations = new ArrayList<>();
        Function<String, String> resolver = key -> resolveConditionValue(conditionBag, key);
        for (ExtensionLoader.ActivateEntry<GatewayFilter> entry : extensionLoader.getActivateEntries(GROUP_GLOBAL, resolver)) {
            invocations.add(new FilterInvocation(entry.name(), entry.instance(), FilterFailPolicy.FAIL_CLOSE));
        }
        for (ExtensionLoader.ActivateEntry<GatewayFilter> entry : extensionLoader.getActivateEntries(GROUP_ROUTE, resolver)) {
            invocations.add(new FilterInvocation(entry.name(), entry.instance(), FilterFailPolicy.FAIL_CLOSE));
        }

        if (bindings != null && !bindings.isEmpty()) {
            List<PluginBinding> sorted = bindings.stream()
                    .filter(PluginBinding::enabled)
                    .filter(binding -> binding.routeId() == null || binding.routeId().isBlank() || binding.routeId().equals(routeId))
                    .sorted(Comparator.comparingInt(PluginBinding::order).thenComparing(PluginBinding::name))
                    .toList();
            for (PluginBinding binding : sorted) {
                GatewayFilter filter = extensionLoader.getExtension(binding.name());
                invocations.add(new FilterInvocation(binding.name(), filter, binding.failPolicy()));
            }
        }

        return new FilterExecutionPlan(invocations);
    }

    private String resolveConditionValue(Map<String, Object> conditionBag, String key) {
        if (conditionBag == null || key == null || key.isBlank()) {
            return null;
        }
        Object value = conditionBag.get(key);
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }
}
