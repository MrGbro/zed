package io.homeey.gateway.governance.api;

public interface PolicyHandler<T extends GovernancePolicy> {
    String ability();
}
