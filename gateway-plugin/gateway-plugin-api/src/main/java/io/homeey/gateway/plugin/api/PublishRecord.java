package io.homeey.gateway.plugin.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class PublishRecord {
    private final String version;
    private final String publishedAt;
    private final String operator;
    private final String summary;
    private final Map<String, Object> status;

    @JsonCreator
    public PublishRecord(
            @JsonProperty("version") String version,
            @JsonProperty("publishedAt") String publishedAt,
            @JsonProperty("operator") String operator,
            @JsonProperty("summary") String summary,
            @JsonProperty("status") Map<String, Object> status
    ) {
        this.version = Objects.requireNonNull(version, "version");
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
        this.operator = operator == null ? "system" : operator;
        this.summary = summary == null ? "" : summary;
        this.status = status == null ? Map.of() : Collections.unmodifiableMap(status);
    }

    @JsonProperty("version")
    public String version() {
        return version;
    }

    @JsonProperty("publishedAt")
    public String publishedAt() {
        return publishedAt;
    }

    @JsonProperty("operator")
    public String operator() {
        return operator;
    }

    @JsonProperty("summary")
    public String summary() {
        return summary;
    }

    @JsonProperty("status")
    public Map<String, Object> status() {
        return status;
    }
}
