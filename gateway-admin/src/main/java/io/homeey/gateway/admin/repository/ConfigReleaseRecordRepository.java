package io.homeey.gateway.admin.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.homeey.gateway.admin.model.ReleaseRecord;
import io.homeey.gateway.config.api.ConfigProvider;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class ConfigReleaseRecordRepository implements ReleaseRecordRepository {
    private static final String RECORDS_DATA_ID = "gateway.release.records.json";
    private static final String GROUP = "GATEWAY";
    private static final int MAX_RECORDS = 200;

    private final ConfigProvider configProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<ReleaseRecord> localCache = new CopyOnWriteArrayList<>();

    public ConfigReleaseRecordRepository(ConfigProvider configProvider) {
        this.configProvider = configProvider;
        this.localCache.addAll(loadRemoteRecords());
    }

    @Override
    public void save(ReleaseRecord record) {
        List<ReleaseRecord> current = new ArrayList<>(loadRemoteRecords());
        boolean updated = false;
        for (int i = 0; i < current.size(); i++) {
            if (record.releaseId().equals(current.get(i).releaseId())) {
                current.set(i, record);
                updated = true;
                break;
            }
        }
        if (!updated) {
            current.add(record);
        }
        trimToMax(current);
        if (persist(current)) {
            localCache.clear();
            localCache.addAll(current);
            return;
        }
        List<ReleaseRecord> fallback = new ArrayList<>(localCache);
        boolean fallbackUpdated = false;
        for (int i = 0; i < fallback.size(); i++) {
            if (record.releaseId().equals(fallback.get(i).releaseId())) {
                fallback.set(i, record);
                fallbackUpdated = true;
                break;
            }
        }
        if (!fallbackUpdated) {
            fallback.add(record);
        }
        trimToMax(fallback);
        localCache.clear();
        localCache.addAll(fallback);
    }

    @Override
    public Optional<ReleaseRecord> findById(String releaseId) {
        List<ReleaseRecord> records = list();
        for (ReleaseRecord record : records) {
            if (record.releaseId().equals(releaseId)) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<ReleaseRecord> list() {
        List<ReleaseRecord> remote = loadRemoteRecords();
        if (!remote.isEmpty()) {
            localCache.clear();
            localCache.addAll(remote);
            return reverse(remote);
        }
        return reverse(new ArrayList<>(localCache));
    }

    private List<ReleaseRecord> loadRemoteRecords() {
        try {
            String payload = configProvider.get(RECORDS_DATA_ID, GROUP).toCompletableFuture().join();
            if (payload == null || payload.isBlank()) {
                return List.of();
            }
            List<Map<String, Object>> rows = objectMapper.readValue(payload, new TypeReference<>() {
            });
            List<ReleaseRecord> records = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                records.add(objectMapper.convertValue(row, ReleaseRecord.class));
            }
            return List.copyOf(records);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private boolean persist(List<ReleaseRecord> records) {
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (ReleaseRecord record : records) {
                rows.add(objectMapper.convertValue(record, new TypeReference<>() {
                }));
            }
            String payload = objectMapper.writeValueAsString(rows);
            return configProvider.publish(RECORDS_DATA_ID, GROUP, payload).toCompletableFuture().join();
        } catch (Exception ex) {
            return false;
        }
    }

    private void trimToMax(List<ReleaseRecord> records) {
        if (records.size() <= MAX_RECORDS) {
            return;
        }
        int start = records.size() - MAX_RECORDS;
        List<ReleaseRecord> kept = new ArrayList<>(records.subList(start, records.size()));
        records.clear();
        records.addAll(kept);
    }

    private List<ReleaseRecord> reverse(List<ReleaseRecord> records) {
        List<ReleaseRecord> copy = new ArrayList<>(records);
        Collections.reverse(copy);
        return List.copyOf(copy);
    }
}
