package io.homeey.gateway.plugin.api;

/**
 * 过滤器失败策略枚举。
 * <p>
 * 定义当过滤器执行失败时的处理策略：
 * <ul>
 *   <li>{@link #FAIL_CLOSE} - 失败关闭：中断请求处理，返回错误响应</li>
 *   <li>{@link #FAIL_OPEN} - 失败开放：忽略错误，继续处理请求</li>
 * </ul>
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public enum FilterFailPolicy {
    /**
     * 失败关闭策略：当过滤器执行失败时，中断请求处理并返回错误响应。
     * <p>
     * 适用于安全关键的过滤器（如鉴权、限流），确保在异常情况下不会放行未授权的请求。
     * </p>
     */
    FAIL_CLOSE,
    
    /**
     * 失败开放策略：当过滤器执行失败时，忽略错误并继续处理请求。
     * <p>
     * 适用于非关键过滤器（如日志记录、指标收集），确保核心业务流程不受影响。
     * </p>
     */
    FAIL_OPEN
}
