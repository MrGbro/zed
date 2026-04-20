package io.homeey.gateway.governance.api;

import java.util.Set;

public record DegradePolicy(
        boolean enabled,
        FailureMode failureMode,
        int status,
        String contentType,
        String body,
        Set<String> triggerOn
) implements GovernancePolicy {
    public static final String ABILITY = "degrade";

    @Override
    public String ability() {
        return ABILITY;
    }

    public static DegradePolicy defaultPolicy() {
        return new DegradePolicy(
                false,
                FailureMode.FAIL_OPEN,
                503,
                "text/plain; charset=UTF-8",
                "service degraded",
                Set.of(
                        GovernanceFailureKind.RATE_LIMITED.code(),
                        GovernanceFailureKind.CIRCUIT_OPEN.code(),
                        GovernanceFailureKind.TIMEOUT.code(),
                        GovernanceFailureKind.RETRY_EXHAUSTED.code(),
                        GovernanceFailureKind.GOVERNANCE_ERROR.code()
                )
        );
    }

    public static DegradePolicy disabled() {
        return defaultPolicy();
    }

    public DegradePolicy {
        failureMode = failureMode == null ? FailureMode.FAIL_OPEN : failureMode;
        status = Math.max(100, status);
        contentType = contentType == null || contentType.isBlank() ? "text/plain; charset=UTF-8" : contentType;
        body = body == null ? "" : body;
        triggerOn = triggerOn == null ? Set.of() : Set.copyOf(triggerOn);
    }
}
