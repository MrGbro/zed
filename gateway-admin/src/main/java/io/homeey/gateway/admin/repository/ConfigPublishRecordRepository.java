package io.homeey.gateway.admin.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.homeey.gateway.config.api.ConfigProvider;
import io.homeey.gateway.plugin.api.PublishRecord;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 基于配置中心的发布记录仓库实现。
 * <p>
 * 该仓库将发布记录持久化到配置中心（如Nacos），支持本地缓存和远程同步。
 * 最多保留200条发布记录，超出时自动清理旧记录。
 * </p>
 *
 * @author tahong[jt4mrg@gmail.com]
 * @date 2026/04/18
 */
@Repository
public class ConfigPublishRecordRepository implements PublishRecordRepository {
    private static final String RECORDS_DATA_ID = "gateway.publish.records.json";
    private static final String GROUP = "GATEWAY";
    private static final int MAX_RECORDS = 200;

    private final ConfigProvider configProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<PublishRecord> localCache = new CopyOnWriteArrayList<>();

    public ConfigPublishRecordRepository(ConfigProvider configProvider) {
        this.configProvider = configProvider;
        this.localCache.addAll(loadRemoteRecords());
    }

    @Override
    public void save(PublishRecord record) {
        List<PublishRecord> current = new ArrayList<>(loadRemoteRecords());
        current.add(record);
        trimToMax(current);
        if (persist(current)) {
            localCache.clear();
            localCache.addAll(current);
            return;
        }
        List<PublishRecord> fallback = new ArrayList<>(localCache);
        fallback.add(record);
        trimToMax(fallback);
        localCache.clear();
        localCache.addAll(fallback);
    }

    @Override
    public List<PublishRecord> list() {
        List<PublishRecord> remote = loadRemoteRecords();
        if (!remote.isEmpty()) {
            localCache.clear();
            localCache.addAll(remote);
            return reverse(remote);
        }
        return reverse(new ArrayList<>(localCache));
    }

    private List<PublishRecord> loadRemoteRecords() {
        try {
            String payload = configProvider.get(RECORDS_DATA_ID, GROUP).toCompletableFuture().join();
            if (payload == null || payload.isBlank()) {
                return List.of();
            }
            List<Map<String, Object>> rows = objectMapper.readValue(payload, new TypeReference<>() {
            });
            List<PublishRecord> records = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                records.add(fromMap(row));
            }
            return List.copyOf(records);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private boolean persist(List<PublishRecord> records) {
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (PublishRecord record : records) {
                rows.add(toMap(record));
            }
            String payload = objectMapper.writeValueAsString(rows);
            return configProvider.publish(RECORDS_DATA_ID, GROUP, payload).toCompletableFuture().join();
        } catch (Exception ex) {
            return false;
        }
    }

    private void trimToMax(List<PublishRecord> records) {
        if (records.size() <= MAX_RECORDS) {
            return;
        }
        int start = records.size() - MAX_RECORDS;
        List<PublishRecord> kept = new ArrayList<>(records.subList(start, records.size()));
        records.clear();
        records.addAll(kept);
    }

    private List<PublishRecord> reverse(List<PublishRecord> records) {
        List<PublishRecord> copy = new ArrayList<>(records);
        Collections.reverse(copy);
        return List.copyOf(copy);
    }

    private Map<String, Object> toMap(PublishRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("version", record.version());
        row.put("publishedAt", record.publishedAt());
        row.put("operator", record.operator());
        row.put("summary", record.summary());
        row.put("status", record.status());
        return row;
    }

    private PublishRecord fromMap(Map<String, Object> row) {
        Object statusObj = row.get("status");
        Map<String, Object> status;
        if (statusObj instanceof Map<?, ?> map) {
            status = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    status.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        } else {
            status = Map.of();
        }
        return new PublishRecord(
                String.valueOf(row.getOrDefault("version", "")),
                String.valueOf(row.getOrDefault("publishedAt", "")),
                String.valueOf(row.getOrDefault("operator", "admin")),
                String.valueOf(row.getOrDefault("summary", "")),
                status
        );
    }
}
