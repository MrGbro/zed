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
