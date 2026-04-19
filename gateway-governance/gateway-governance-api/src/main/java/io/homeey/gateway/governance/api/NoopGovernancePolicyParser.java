package io.homeey.gateway.governance.api;

import java.util.Map;

public final class NoopGovernancePolicyParser implements GovernancePolicyParser {
    @Override
    public GovernancePolicy parse(Map<String, Object> entries) {
        return GovernancePolicy.disabled();
    }
}
