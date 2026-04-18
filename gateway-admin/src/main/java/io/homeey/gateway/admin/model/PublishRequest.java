package io.homeey.gateway.admin.model;

import io.homeey.gateway.plugin.api.PluginBinding;
import io.homeey.gateway.plugin.api.PolicySet;

import java.util.List;
import java.util.Map;

/**
 * 发布请求记录，封装路由发布时的完整配置信息。
 * <p>
 * 用于接收管理端的路由发布请求，包含路由列表、插件绑定、策略集以及操作人和摘要信息。
 * </p>
 *
 * @param routes         路由项列表
 * @param pluginBindings 插件绑定列表
 * @param policySet      策略集
 * @param operator       操作人
 * @param summary        发布摘要说明
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public record PublishRequest(
        List<RouteItem> routes,
        List<PluginBinding> pluginBindings,
        PolicySet policySet,
        String operator,
        String summary
) {
    /**
     * 路由项记录，封装单个路由的配置信息。
     *
     * @param id              路由唯一标识
     * @param host            主机名匹配条件
     * @param pathPrefix      路径前缀匹配条件
     * @param method          HTTP方法匹配条件
     * @param headers         HTTP头匹配条件
     * @param upstreamService 上游服务名称
     * @param upstreamPath    上游路径，用于重写请求路径
     */
    public record RouteItem(
            String id,
            String host,
            String pathPrefix,
            String method,
            Map<String, String> headers,
            String upstreamService,
            String upstreamPath
    ) {
    }
}
