package io.homeey.gateway.core.route;

import io.homeey.gateway.plugin.api.PluginBinding;
import io.homeey.gateway.plugin.api.PolicySet;

import java.util.List;
import java.util.Map;

/**
 * 路由定义记录，封装单个路由的完整配置信息。
 * <p>
 * 包含路由匹配条件（host、path、method、headers）、上游服务配置以及插件绑定和策略集。
 * </p>
 *
 * @param id             路由唯一标识
 * @param host           主机名匹配条件
 * @param pathPrefix     路径前缀匹配条件
 * @param method         HTTP方法匹配条件
 * @param headers        HTTP头匹配条件
 * @param upstreamService 上游服务名称
 * @param upstreamPath   上游路径，用于重写请求路径
 * @param pluginBindings 插件绑定列表，指定该路由需要执行的插件
 * @param policySet      策略集，包含限流、熔断等策略配置
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public record RouteDefinition(
        String id,
        String host,
        String pathPrefix,
        String method,
        Map<String, String> headers,
        String upstreamService,
        String upstreamPath,
        List<PluginBinding> pluginBindings,
        PolicySet policySet
) {
    /**
     * 紧凑构造函数，确保pluginBindings和policySet不为null。
     *
     * @param pluginBindings 插件绑定列表，为null时初始化为空列表
     * @param policySet      策略集，为null时初始化为空策略集
     */
    public RouteDefinition {
        pluginBindings = pluginBindings == null ? List.of() : List.copyOf(pluginBindings);
        policySet = policySet == null ? new PolicySet(Map.of()) : policySet;
    }
}
