package io.homeey.gateway.core.runtime;

import java.util.Map;

/**
 * 网关运行时类，管理网关的运行时状态。
 * <p>
 * 封装了快照管理器，提供对路由快照和配置的管理能力。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public final class GatewayRuntime {
    private final RuntimeSnapshotManager snapshotManager;

    /**
     * 构造网关运行时。
     *
     * @param initialSnapshot 初始快照数据
     */
    public GatewayRuntime(Map<String, Object> initialSnapshot) {
        this.snapshotManager = new RuntimeSnapshotManager(initialSnapshot);
    }

    /**
     * 获取快照管理器。
     *
     * @return 运行时快照管理器
     */
    public RuntimeSnapshotManager snapshotManager() {
        return snapshotManager;
    }
}
