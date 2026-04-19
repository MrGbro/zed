package io.homeey.gateway.admin.repository;

import io.homeey.gateway.admin.model.ReleaseRecord;

import java.util.List;
import java.util.Optional;

public interface ReleaseRecordRepository {
    void save(ReleaseRecord record);

    Optional<ReleaseRecord> findById(String releaseId);

    List<ReleaseRecord> list();
}
