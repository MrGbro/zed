package io.homeey.gateway.admin.repository;

import io.homeey.gateway.plugin.api.PublishRecord;

import java.util.List;

/**
 * 发布记录仓库接口，提供发布记录的持久化操作。
 * <p>
 * 用于保存和查询路由发布历史记录。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
public interface PublishRecordRepository {
    /**
     * 保存发布记录。
     *
     * @param record 发布记录
     */
    void save(PublishRecord record);

    /**
     * 查询所有发布记录。
     *
     * @return 发布记录列表
     */
    List<PublishRecord> list();
}
