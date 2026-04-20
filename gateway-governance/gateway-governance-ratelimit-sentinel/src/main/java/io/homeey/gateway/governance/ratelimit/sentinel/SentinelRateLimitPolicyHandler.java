package io.homeey.gateway.governance.ratelimit.sentinel;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import io.homeey.gateway.governance.api.GovernanceExecutionContext;
import io.homeey.gateway.governance.api.GovernanceStateStore;
import io.homeey.gateway.governance.api.RateLimitPolicy;
import io.homeey.gateway.governance.api.RateLimitPolicyHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SentinelRateLimitPolicyHandler implements RateLimitPolicyHandler {
    private static final String RESOURCE_PREFIX = "gateway:ratelimit:";
    private static final ConcurrentMap<String, RuleSpec> RULE_SPECS = new ConcurrentHashMap<>();

    @Override
    public boolean allow(GovernanceExecutionContext context, RateLimitPolicy policy, GovernanceStateStore stateStore) {
        if (!policy.enabled()) {
            return true;
        }
        String resource = RESOURCE_PREFIX + resolveKey(context, policy);
        refreshRuleIfChanged(resource, policy.qps());

        Entry entry = null;
        try {
            entry = SphU.entry(resource);
            return true;
        } catch (BlockException blocked) {
            return false;
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    private void refreshRuleIfChanged(String resource, double qps) {
        if (qps <= 0D) {
            return;
        }
        RuleSpec next = new RuleSpec(resource, qps);
        RuleSpec previous = RULE_SPECS.put(resource, next);
        if (next.equals(previous)) {
            return;
        }

        List<FlowRule> rules = new ArrayList<>(RULE_SPECS.size());
        for (RuleSpec spec : RULE_SPECS.values()) {
            rules.add(createQpsRule(spec.resource(), spec.qps()));
        }
        FlowRuleManager.loadRules(rules);
    }

    private FlowRule createQpsRule(String resource, double qps) {
        FlowRule rule = new FlowRule(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(qps);
        return rule;
    }

    private String resolveKey(GovernanceExecutionContext context, RateLimitPolicy policy) {
        if ("ip".equals(policy.keyType())) {
            String forwarded = header(context, "x-forwarded-for");
            if (forwarded != null && !forwarded.isBlank()) {
                return "ip:" + firstIp(forwarded);
            }
        }
        if ("header".equals(policy.keyType())) {
            String value = header(context, policy.keyHeader());
            if (value != null && !value.isBlank()) {
                return "header:" + policy.keyHeader() + ":" + value;
            }
        }
        return "route:" + (context.routeId() == null ? "" : context.routeId());
    }

    private String firstIp(String forwarded) {
        int comma = forwarded.indexOf(',');
        if (comma < 0) {
            return forwarded.trim();
        }
        return forwarded.substring(0, comma).trim();
    }

    private String header(GovernanceExecutionContext context, String name) {
        if (name == null || name.isBlank() || context.gatewayContext().request() == null) {
            return null;
        }
        Map<String, String> headers = context.gatewayContext().request().headers();
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private record RuleSpec(String resource, double qps) {
        private RuleSpec {
            resource = Objects.requireNonNull(resource, "resource");
        }
    }
}
