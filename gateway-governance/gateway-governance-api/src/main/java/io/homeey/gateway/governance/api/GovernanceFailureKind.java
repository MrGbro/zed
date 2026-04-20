package io.homeey.gateway.governance.api;

public enum GovernanceFailureKind {
    RATE_LIMITED("rate_limited"),
    CIRCUIT_OPEN("circuit_open"),
    TIMEOUT("timeout"),
    RETRY_EXHAUSTED("retry_exhausted"),
    GOVERNANCE_ERROR("governance_error");

    private final String code;

    GovernanceFailureKind(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
