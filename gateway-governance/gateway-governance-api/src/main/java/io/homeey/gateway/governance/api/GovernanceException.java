package io.homeey.gateway.governance.api;

public final class GovernanceException extends RuntimeException {
    private final GovernanceFailureKind kind;

    public GovernanceException(GovernanceFailureKind kind, String message) {
        super(message);
        this.kind = kind == null ? GovernanceFailureKind.GOVERNANCE_ERROR : kind;
    }

    public GovernanceException(GovernanceFailureKind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind == null ? GovernanceFailureKind.GOVERNANCE_ERROR : kind;
    }

    public GovernanceFailureKind kind() {
        return kind;
    }
}
