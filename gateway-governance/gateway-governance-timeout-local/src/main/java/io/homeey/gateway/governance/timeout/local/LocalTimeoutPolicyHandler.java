package io.homeey.gateway.governance.timeout.local;

import io.homeey.gateway.governance.api.GovernanceScheduler;
import io.homeey.gateway.governance.api.TimeoutPolicy;
import io.homeey.gateway.governance.api.TimeoutPolicyHandler;
import io.homeey.gateway.governance.api.GovernanceExecutionContext;
import io.homeey.gateway.transport.api.HttpResponseMessage;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class LocalTimeoutPolicyHandler implements TimeoutPolicyHandler {
    @Override
    public CompletionStage<HttpResponseMessage> execute(
            GovernanceExecutionContext context,
            TimeoutPolicy policy,
            Supplier<CompletionStage<HttpResponseMessage>> attempt,
            GovernanceScheduler scheduler
    ) {
        CompletionStage<HttpResponseMessage> stage = attempt.get();
        if (!policy.enabled()) {
            return stage;
        }
        return scheduler.withTimeout(stage, policy.durationMillis(), "request timeout");
    }
}
