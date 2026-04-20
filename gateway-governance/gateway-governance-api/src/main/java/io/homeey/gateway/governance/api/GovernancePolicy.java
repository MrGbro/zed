package io.homeey.gateway.governance.api;

public interface GovernancePolicy {
    String ability();

    boolean enabled();

    FailureMode failureMode();
}
