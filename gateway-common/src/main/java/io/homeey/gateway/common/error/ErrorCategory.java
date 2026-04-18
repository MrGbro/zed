package io.homeey.gateway.common.error;

/**
 * 错误分类枚举，定义网关系统中可能出现的错误类型。
 * <p>
 * 用于对错误进行归类，便于错误处理、监控和告警。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public enum ErrorCategory {
    /** 客户端错误，如请求参数无效、认证失败等 */
    CLIENT_ERROR,
    /** 上游服务错误，如后端服务返回5xx、超时等 */
    UPSTREAM_ERROR,
    /** 系统内部错误，如资源不足、配置错误等 */
    SYSTEM_ERROR,
    /** 控制平面错误，如配置同步失败、路由更新异常等 */
    CONTROL_PLANE_ERROR
}