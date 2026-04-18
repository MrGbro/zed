package io.homeey.gateway.admin.repository;

import io.homeey.gateway.plugin.api.PublishRecord;

import java.util.List;

public interface PublishRecordRepository {
    void save(PublishRecord record);

    List<PublishRecord> list();
}
