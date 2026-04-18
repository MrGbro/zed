package io.homeey.gateway.plugin.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class PublishRecord {
    private final String version;
    private final String publishedAt;
    private final String operator;
    private final String summary;
    private final Map<String, Object> status;

    public PublishRecord(String version, String publishedAt, String operator, String summary, Map<String, Object> status) {
        this.version = Objects.requireNonNull(version, "version");
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
        this.operator = operator == null ? "system" : operator;
        this.summary = summary == null ? "" : summary;
        this.status = status == null ? Map.of() : Collections.unmodifiableMap(status);
    }

    public String version() {
        return version;
    }

    public String publishedAt() {
        return publishedAt;
    }

    public String operator() {
        return operator;
    }

    public String summary() {
        return summary;
    }

    public Map<String, Object> status() {
        return status;
    }
}
