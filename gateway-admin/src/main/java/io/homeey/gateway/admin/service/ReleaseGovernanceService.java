package io.homeey.gateway.admin.service;

import io.homeey.gateway.admin.model.PublishRequest;
import io.homeey.gateway.admin.model.ReleaseRecord;
import io.homeey.gateway.admin.model.ReleaseState;
import io.homeey.gateway.admin.repository.ReleaseRecordRepository;
import io.homeey.gateway.plugin.api.PluginBinding;
import io.homeey.gateway.plugin.api.PolicySet;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ReleaseGovernanceService {
    private final PublishService publishService;
    private final ReleaseRecordRepository releaseRecordRepository;

    public ReleaseGovernanceService(PublishService publishService, ReleaseRecordRepository releaseRecordRepository) {
        this.publishService = publishService;
        this.releaseRecordRepository = releaseRecordRepository;
    }

    public ReleaseRecord createDraft(
            List<PublishRequest.RouteItem> routes,
            List<io.homeey.gateway.plugin.api.PluginBinding> bindings,
            DraftCommand command
    ) {
        String releaseId = "r-" + System.currentTimeMillis();
        String now = Instant.now().toString();
        ReleaseRecord record = new ReleaseRecord(
                releaseId,
                now,
                command.operator() == null || command.operator().isBlank() ? "admin" : command.operator(),
                command.summary() == null ? "release draft" : command.summary(),
                ReleaseState.DRAFT,
                routes == null ? List.of() : List.copyOf(routes),
                bindings == null ? List.of() : List.copyOf(bindings),
                command.policySet() == null ? Map.of() : command.policySet(),
                "",
                "",
                "",
                command.canary() == null ? ReleaseRecord.CanaryPolicy.disabled() : command.canary(),
                command.autoRollback() == null ? ReleaseRecord.AutoRollbackPolicy.disabled() : command.autoRollback(),
                "",
                "",
                "",
                ""
        );
        releaseRecordRepository.save(record);
        return record;
    }

    public ReleaseRecord validate(String releaseId) {
        ReleaseRecord record = findRequired(releaseId);
        if (record.state() != ReleaseState.DRAFT) {
            throw new IllegalArgumentException("release state must be DRAFT for validate: " + releaseId);
        }
        PublishRequest request = toPublishRequest(record);
        publishService.validate(request);
        ReleaseRecord validated = record.withState(ReleaseState.VALIDATED);
        releaseRecordRepository.save(validated);
        return validated;
    }

    public ReleaseRecord approve(String releaseId, ApproveCommand command) {
        ReleaseRecord record = findRequired(releaseId);
        if (record.state() != ReleaseState.VALIDATED) {
            throw new IllegalArgumentException("release state must be VALIDATED for approve: " + releaseId);
        }
        String approver = command == null || command.approver() == null || command.approver().isBlank()
                ? "reviewer"
                : command.approver();
        String comment = command == null || command.comment() == null ? "" : command.comment();
        ReleaseRecord approved = record.withApproval(approver, Instant.now().toString(), comment);
        releaseRecordRepository.save(approved);
        return approved;
    }

    public ReleaseRecord publish(String releaseId) {
        ReleaseRecord record = findRequired(releaseId);
        if (record.state() != ReleaseState.APPROVED) {
            throw new IllegalArgumentException("release state must be APPROVED for publish: " + releaseId);
        }
        Map<String, Object> result = publishService.publish(resolvePublishRequest(record));
        String version = String.valueOf(result.getOrDefault("version", ""));
        String publishedAt = String.valueOf(result.getOrDefault("publishedAt", Instant.now().toString()));
        ReleaseRecord published = record.withPublished(version, publishedAt);
        releaseRecordRepository.save(published);
        return published;
    }

    public ReleaseRecord rollback(String releaseId, RollbackCommand command) {
        ReleaseRecord record = findRequired(releaseId);
        if (record.state() != ReleaseState.PUBLISHED) {
            throw new IllegalArgumentException("release state must be PUBLISHED for rollback: " + releaseId);
        }
        String targetReleaseId = command == null ? "" : command.targetReleaseId();
        if (targetReleaseId == null || targetReleaseId.isBlank()) {
            throw new IllegalArgumentException("rollback targetReleaseId cannot be blank");
        }
        ReleaseRecord target = findRequired(targetReleaseId);
        if (target.state() != ReleaseState.PUBLISHED && target.state() != ReleaseState.ROLLED_BACK) {
            throw new IllegalArgumentException("rollback target release must be published/rolled_back: " + targetReleaseId);
        }
        publishService.publish(toPublishRequest(target));
        String comment = command.comment() == null ? "" : command.comment();
        ReleaseRecord rolled = record.withRollback(targetReleaseId, comment);
        releaseRecordRepository.save(rolled);
        return rolled;
    }

    public Optional<ReleaseRecord> get(String releaseId) {
        return releaseRecordRepository.findById(releaseId);
    }

    public List<ReleaseRecord> list() {
        return releaseRecordRepository.list();
    }

    public AutoRollbackEvaluationResult evaluateAutoRollback(String releaseId, AutoRollbackMetricsSnapshot metrics) {
        ReleaseRecord record = findRequired(releaseId);
        if (record.state() != ReleaseState.PUBLISHED) {
            throw new IllegalArgumentException("release state must be PUBLISHED for auto rollback evaluate: " + releaseId);
        }
        ReleaseRecord.AutoRollbackPolicy policy = record.autoRollback();
        if (policy == null || !policy.enabled()) {
            return new AutoRollbackEvaluationResult(false, record, List.of());
        }
        List<String> reasons = evaluateSloReasons(policy, metrics == null ? new AutoRollbackMetricsSnapshot(null, null, null) : metrics);
        if (reasons.isEmpty()) {
            return new AutoRollbackEvaluationResult(false, record, List.of());
        }
        String targetReleaseId = policy.targetReleaseId() == null || policy.targetReleaseId().isBlank()
                ? findPreviousPublishedReleaseId(record.releaseId())
                : policy.targetReleaseId();
        if (targetReleaseId == null || targetReleaseId.isBlank()) {
            throw new IllegalArgumentException("auto rollback target release cannot be resolved");
        }
        String comment = "auto rollback triggered: " + String.join("; ", reasons);
        ReleaseRecord rolled = rollback(
                releaseId,
                new RollbackCommand("auto-rollback", comment, targetReleaseId)
        );
        return new AutoRollbackEvaluationResult(true, rolled, List.copyOf(reasons));
    }

    private List<String> evaluateSloReasons(ReleaseRecord.AutoRollbackPolicy policy, AutoRollbackMetricsSnapshot metrics) {
        List<String> reasons = new ArrayList<>();
        if (policy.maxErrorRate() != null && metrics.errorRate() != null && metrics.errorRate() > policy.maxErrorRate()) {
            reasons.add("errorRate>" + policy.maxErrorRate());
        }
        if (policy.maxP95LatencyMillis() != null
                && metrics.p95LatencyMillis() != null
                && metrics.p95LatencyMillis() > policy.maxP95LatencyMillis()) {
            reasons.add("p95LatencyMillis>" + policy.maxP95LatencyMillis());
        }
        if (policy.minAvailability() != null && metrics.availability() != null && metrics.availability() < policy.minAvailability()) {
            reasons.add("availability<" + policy.minAvailability());
        }
        return List.copyOf(reasons);
    }

    private PublishRequest resolvePublishRequest(ReleaseRecord record) {
        ReleaseRecord.CanaryPolicy canary = record.canary();
        if (canary == null || !canary.enabled()) {
            return toPublishRequest(record);
        }
        if (!"header".equalsIgnoreCase(canary.mode())) {
            throw new IllegalArgumentException("unsupported canary mode: " + canary.mode());
        }
        if (canary.header() == null || canary.header().isBlank() || canary.value() == null || canary.value().isBlank()) {
            throw new IllegalArgumentException("canary header/value cannot be blank when canary enabled");
        }
        ReleaseRecord baseline = findPreviousPublishedRelease(record.releaseId())
                .orElseThrow(() -> new IllegalArgumentException("no stable baseline release found for canary publish"));

        List<PublishRequest.RouteItem> canaryRoutes = new ArrayList<>();
        Map<String, String> canaryRouteIdMap = new LinkedHashMap<>();
        for (PublishRequest.RouteItem route : record.routes()) {
            String canaryRouteId = route.id() + "__canary__" + record.releaseId();
            Map<String, String> headers = new LinkedHashMap<>();
            if (route.headers() != null) {
                headers.putAll(route.headers());
            }
            headers.put(canary.header(), canary.value());
            canaryRoutes.add(new PublishRequest.RouteItem(
                    canaryRouteId,
                    route.host(),
                    route.pathPrefix(),
                    route.method(),
                    Map.copyOf(headers),
                    route.upstreamService(),
                    route.upstreamPath()
            ));
            canaryRouteIdMap.put(route.id(), canaryRouteId);
        }

        List<PluginBinding> bindings = new ArrayList<>();
        if (baseline.pluginBindings() != null) {
            bindings.addAll(baseline.pluginBindings());
        }
        if (record.pluginBindings() != null) {
            for (PluginBinding binding : record.pluginBindings()) {
                String routeId = binding.routeId();
                if (routeId == null || routeId.isBlank()) {
                    continue;
                }
                String canaryRouteId = canaryRouteIdMap.get(routeId);
                if (canaryRouteId == null || canaryRouteId.isBlank()) {
                    continue;
                }
                bindings.add(new PluginBinding(
                        binding.name(),
                        canaryRouteId,
                        binding.order(),
                        binding.enabled(),
                        binding.failPolicy(),
                        binding.config()
                ));
            }
        }

        List<PublishRequest.RouteItem> routes = new ArrayList<>(canaryRoutes);
        if (baseline.routes() != null) {
            routes.addAll(baseline.routes());
        }
        return new PublishRequest(
                List.copyOf(routes),
                List.copyOf(bindings),
                new PolicySet(record.policySet()),
                record.operator(),
                record.summary()
        );
    }

    private String findPreviousPublishedReleaseId(String currentReleaseId) {
        return findPreviousPublishedRelease(currentReleaseId).map(ReleaseRecord::releaseId).orElse("");
    }

    private Optional<ReleaseRecord> findPreviousPublishedRelease(String currentReleaseId) {
        List<ReleaseRecord> records = releaseRecordRepository.list();
        for (ReleaseRecord candidate : records) {
            if (candidate == null) {
                continue;
            }
            if (candidate.releaseId().equals(currentReleaseId)) {
                continue;
            }
            if (candidate.state() == ReleaseState.PUBLISHED || candidate.state() == ReleaseState.ROLLED_BACK) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private PublishRequest toPublishRequest(ReleaseRecord record) {
        return new PublishRequest(
                record.routes(),
                record.pluginBindings(),
                new PolicySet(record.policySet()),
                record.operator(),
                record.summary()
        );
    }

    private ReleaseRecord findRequired(String releaseId) {
        return releaseRecordRepository.findById(releaseId)
                .orElseThrow(() -> new IllegalArgumentException("release not found: " + releaseId));
    }

    public record DraftCommand(
            String operator,
            String summary,
            Map<String, Object> policySet,
            ReleaseRecord.CanaryPolicy canary,
            ReleaseRecord.AutoRollbackPolicy autoRollback
    ) {
    }

    public record ApproveCommand(String approver, String comment) {
    }

    public record RollbackCommand(String operator, String comment, String targetReleaseId) {
    }

    public record AutoRollbackMetricsSnapshot(
            Double errorRate,
            Long p95LatencyMillis,
            Double availability
    ) {
    }

    public record AutoRollbackEvaluationResult(
            boolean triggered,
            ReleaseRecord release,
            List<String> reasons
    ) {
    }
}
