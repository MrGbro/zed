package io.homeey.gateway.core.route;

import java.util.List;

/**
 * 路由表快照，封装某一时刻的完整路由配置。
 * <p>
 * 该快照用于原子性地更新路由表，确保路由变更的一致性。
 * 每次路由配置变更都会生成新的版本号。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class RouteTableSnapshot {
    private final String version;
    private final List<RouteDefinition> routes;

    /**
     * 构造路由表快照。
     *
     * @param version 版本号，用于标识快照版本
     * @param routes  路由定义列表
     */
    public RouteTableSnapshot(String version, List<RouteDefinition> routes) {
        this.version = version;
        this.routes = List.copyOf(routes);
    }

    /**
     * 获取快照版本号。
     *
     * @return 版本号
     */
    public String version() {
        return version;
    }

    /**
     * 获取路由定义列表。
     *
     * @return 不可变的路由定义列表
     */
    public List<RouteDefinition> routes() {
        return routes;
    }
}
