package io.homeey.gateway.config.api;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * 配置提供者接口，提供配置的获取、发布和订阅功能。
 * <p>
 * 该接口封装了配置中心的操作，支持异步获取配置、发布配置变更以及监听配置变化。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public interface ConfigProvider {
    /**
     * 获取配置内容。
     *
     * @param dataId 配置ID
     * @param group  配置分组
     * @return 配置内容的异步结果
     */
    CompletionStage<String> get(String dataId, String group);

    /**
     * 发布配置内容。
     *
     * @param dataId  配置ID
     * @param group   配置分组
     * @param content 配置内容
     * @return 是否发布成功的异步结果
     */
    CompletionStage<Boolean> publish(String dataId, String group, String content);

    /**
     * 订阅配置变更。
     * <p>
     * 当配置发生变化时，监听器会被调用。
     * </p>
     *
     * @param dataId   配置ID
     * @param group    配置分组
     * @param listener 配置变更监听器
     * @return 订阅操作的异步结果
     */
    CompletionStage<Void> subscribe(String dataId, String group, Consumer<String> listener);
}
