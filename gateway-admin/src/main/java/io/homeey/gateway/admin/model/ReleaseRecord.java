package io.homeey.gateway.admin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.homeey.gateway.plugin.api.PluginBinding;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ReleaseRecord {
    private final String releaseId;
    private final String createdAt;
    private final String operator;
    private final String summary;
    private final ReleaseState state;
    private final List<PublishRequest.RouteItem> routes;
    private final List<PluginBinding> pluginBindings;
    private final Map<String, Object> policySet;
    private final String approvedBy;
    private final String approvedAt;
    private final String approvalComment;
    private final CanaryPolicy canary;
    private final AutoRollbackPolicy autoRollback;
    private final String publishedVersion;
    private final String publishedAt;
    private final String rollbackToReleaseId;
    private final String rollbackComment;

    @JsonCreator
    public ReleaseRecord(
            @JsonProperty("releaseId") String releaseId,
            @JsonProperty("createdAt") String createdAt,
            @JsonProperty("operator") String operator,
            @JsonProperty("summary") String summary,
            @JsonProperty("state") ReleaseState state,
            @JsonProperty("routes") List<PublishRequest.RouteItem> routes,
            @JsonProperty("pluginBindings") List<PluginBinding> pluginBindings,
            @JsonProperty("policySet") Map<String, Object> policySet,
            @JsonProperty("approvedBy") String approvedBy,
            @JsonProperty("approvedAt") String approvedAt,
            @JsonProperty("approvalComment") String approvalComment,
            @JsonProperty("canary") CanaryPolicy canary,
            @JsonProperty("autoRollback") AutoRollbackPolicy autoRollback,
            @JsonProperty("publishedVersion") String publishedVersion,
            @JsonProperty("publishedAt") String publishedAt,
            @JsonProperty("rollbackToReleaseId") String rollbackToReleaseId,
            @JsonProperty("rollbackComment") String rollbackComment
    ) {
        this.releaseId = Objects.requireNonNull(releaseId, "releaseId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.operator = operator == null ? "admin" : operator;
        this.summary = summary == null ? "" : summary;
        this.state = state == null ? ReleaseState.DRAFT : state;
        this.routes = routes == null ? List.of() : Collections.unmodifiableList(routes);
        this.pluginBindings = pluginBindings == null ? List.of() : Collections.unmodifiableList(pluginBindings);
        this.policySet = policySet == null ? Map.of() : Collections.unmodifiableMap(policySet);
        this.approvedBy = approvedBy == null ? "" : approvedBy;
        this.approvedAt = approvedAt == null ? "" : approvedAt;
        this.approvalComment = approvalComment == null ? "" : approvalComment;
        this.canary = canary == null ? CanaryPolicy.disabled() : canary;
        this.autoRollback = autoRollback == null ? AutoRollbackPolicy.disabled() : autoRollback;
        this.publishedVersion = publishedVersion == null ? "" : publishedVersion;
        this.publishedAt = publishedAt == null ? "" : publishedAt;
        this.rollbackToReleaseId = rollbackToReleaseId == null ? "" : rollbackToReleaseId;
        this.rollbackComment = rollbackComment == null ? "" : rollbackComment;
    }

    @JsonProperty("releaseId")
    public String releaseId() {
        return releaseId;
    }

    @JsonProperty("createdAt")
    public String createdAt() {
        return createdAt;
    }

    @JsonProperty("operator")
    public String operator() {
        return operator;
    }

    @JsonProperty("summary")
    public String summary() {
        return summary;
    }

    @JsonProperty("state")
    public ReleaseState state() {
        return state;
    }

    @JsonProperty("routes")
    public List<PublishRequest.RouteItem> routes() {
        return routes;
    }

    @JsonProperty("pluginBindings")
    public List<PluginBinding> pluginBindings() {
        return pluginBindings;
    }

    @JsonProperty("policySet")
    public Map<String, Object> policySet() {
        return policySet;
    }

    @JsonProperty("approvedBy")
    public String approvedBy() {
        return approvedBy;
    }

    @JsonProperty("approvedAt")
    public String approvedAt() {
        return approvedAt;
    }

    @JsonProperty("approvalComment")
    public String approvalComment() {
        return approvalComment;
    }

    @JsonProperty("canary")
    public CanaryPolicy canary() {
        return canary;
    }

    @JsonProperty("autoRollback")
    public AutoRollbackPolicy autoRollback() {
        return autoRollback;
    }

    @JsonProperty("publishedVersion")
    public String publishedVersion() {
        return publishedVersion;
    }

    @JsonProperty("publishedAt")
    public String publishedAt() {
        return publishedAt;
    }

    @JsonProperty("rollbackToReleaseId")
    public String rollbackToReleaseId() {
        return rollbackToReleaseId;
    }

    @JsonProperty("rollbackComment")
    public String rollbackComment() {
        return rollbackComment;
    }

    public ReleaseRecord withState(ReleaseState newState) {
        return new ReleaseRecord(
                releaseId,
                createdAt,
                operator,
                summary,
                newState,
                routes,
                pluginBindings,
                policySet,
                approvedBy,
                approvedAt,
                approvalComment,
                canary,
                autoRollback,
                publishedVersion,
                publishedAt,
                rollbackToReleaseId,
                rollbackComment
        );
    }

    public ReleaseRecord withApproval(String approver, String approvalAt, String comment) {
        return new ReleaseRecord(
                releaseId,
                createdAt,
                operator,
                summary,
                ReleaseState.APPROVED,
                routes,
                pluginBindings,
                policySet,
                approver,
                approvalAt,
                comment,
                canary,
                autoRollback,
                publishedVersion,
                publishedAt,
                rollbackToReleaseId,
                rollbackComment
        );
    }

    public ReleaseRecord withPublished(String version, String publishTime) {
        return new ReleaseRecord(
                releaseId,
                createdAt,
                operator,
                summary,
                ReleaseState.PUBLISHED,
                routes,
                pluginBindings,
                policySet,
                approvedBy,
                approvedAt,
                approvalComment,
                canary,
                autoRollback,
                version,
                publishTime,
                rollbackToReleaseId,
                rollbackComment
        );
    }

    public ReleaseRecord withRollback(String rollbackTargetReleaseId, String comment) {
        return new ReleaseRecord(
                releaseId,
                createdAt,
                operator,
                summary,
                ReleaseState.ROLLED_BACK,
                routes,
                pluginBindings,
                policySet,
                approvedBy,
                approvedAt,
                approvalComment,
                canary,
                autoRollback,
                publishedVersion,
                publishedAt,
                rollbackTargetReleaseId,
                comment
        );
    }

    public record CanaryPolicy(
            @JsonProperty("mode") String mode,
            @JsonProperty("header") String header,
            @JsonProperty("value") String value,
            @JsonProperty("enabled") boolean enabled
    ) {
        @JsonCreator
        public CanaryPolicy {
            mode = mode == null ? "" : mode;
            header = header == null ? "" : header;
            value = value == null ? "" : value;
        }

        public static CanaryPolicy disabled() {
            return new CanaryPolicy("", "", "", false);
        }
    }

    public record AutoRollbackPolicy(
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("maxErrorRate") Double maxErrorRate,
            @JsonProperty("maxP95LatencyMillis") Long maxP95LatencyMillis,
            @JsonProperty("minAvailability") Double minAvailability,
            @JsonProperty("targetReleaseId") String targetReleaseId
    ) {
        @JsonCreator
        public AutoRollbackPolicy {
            targetReleaseId = targetReleaseId == null ? "" : targetReleaseId;
        }

        public static AutoRollbackPolicy disabled() {
            return new AutoRollbackPolicy(false, null, null, null, "");
        }
    }
}
