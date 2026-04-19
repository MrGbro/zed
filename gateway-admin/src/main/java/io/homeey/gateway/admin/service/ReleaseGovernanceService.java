package io.homeey.gateway.admin.service;

import io.homeey.gateway.admin.model.PublishRequest;
import io.homeey.gateway.admin.model.ReleaseRecord;
import io.homeey.gateway.admin.model.ReleaseState;
import io.homeey.gateway.admin.repository.ReleaseRecordRepository;
import io.homeey.gateway.plugin.api.PolicySet;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
        Map<String, Object> result = publishService.publish(toPublishRequest(record));
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
            ReleaseRecord.CanaryPolicy canary
    ) {
    }

    public record ApproveCommand(String approver, String comment) {
    }

    public record RollbackCommand(String operator, String comment, String targetReleaseId) {
    }
}
