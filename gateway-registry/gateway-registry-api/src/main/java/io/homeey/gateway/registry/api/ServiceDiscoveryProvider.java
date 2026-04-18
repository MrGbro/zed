package io.homeey.gateway.registry.api;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * 服务发现提供者接口，提供服务实例的查询、注册和订阅功能。
 * <p>
 * 该接口封装了服务注册中心的操作，支持异步获取服务实例列表、注册服务以及监听服务变更。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public interface ServiceDiscoveryProvider {
    /**
     * 获取服务实例列表。
     *
     * @param serviceName 服务名称
     * @return 服务实例地址列表的异步结果
     */
    CompletionStage<List<String>> getInstances(String serviceName);

    /**
     * 注册服务实例。
     *
     * @param serviceName 服务名称
     * @param endpoint    服务端点（IP:Port）
     * @return 注册操作的异步结果
     */
    CompletionStage<Void> register(String serviceName, String endpoint);

    /**
     * 订阅服务变更。
     * <p>
     * 当服务实例列表发生变化时，监听器会被调用。
     * </p>
     *
     * @param serviceName 服务名称
     * @param listener    服务变更监听器
     * @return 订阅操作的异步结果
     */
    CompletionStage<Void> subscribe(String serviceName, Consumer<List<String>> listener);
}
