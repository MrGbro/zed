package io.homeey.gateway.governance.degrade.local;

import io.homeey.gateway.governance.api.DegradePolicy;
import io.homeey.gateway.governance.api.DegradePolicyHandler;
import io.homeey.gateway.governance.api.GovernanceExecutionContext;
import io.homeey.gateway.governance.api.GovernanceFailureKind;
import io.homeey.gateway.transport.api.HttpResponseMessage;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class LocalDegradePolicyHandler implements DegradePolicyHandler {
    @Override
    public HttpResponseMessage degrade(
            GovernanceExecutionContext context,
            DegradePolicy policy,
            GovernanceFailureKind kind,
            Throwable cause
    ) {
        if (!policy.enabled()) {
            return null;
        }
        if (!policy.triggerOn().contains(kind.code())) {
            return null;
        }
        return new HttpResponseMessage(
                policy.status(),
                Map.of("content-type", policy.contentType()),
                policy.body().getBytes(StandardCharsets.UTF_8)
        );
    }
}
