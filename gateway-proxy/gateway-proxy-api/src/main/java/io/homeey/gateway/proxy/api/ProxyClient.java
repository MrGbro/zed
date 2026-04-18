package io.homeey.gateway.proxy.api;

import java.util.concurrent.CompletionStage;

/**
 * 代理客户端接口，用于执行HTTP请求到上游服务。
 * <p>
 * 该接口封装了与后端服务的通信逻辑，支持异步请求处理。
 * 实现类应该负责连接池管理、超时控制、重试等机制。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public interface ProxyClient extends AutoCloseable {
    /**
     * 执行代理请求。
     *
     * @param request 代理请求对象
     * @return 代理响应的异步结果
     */
    CompletionStage<ProxyResponse> execute(ProxyRequest request);

    /**
     * 关闭代理客户端，释放相关资源。
     */
    @Override
    void close();
}
