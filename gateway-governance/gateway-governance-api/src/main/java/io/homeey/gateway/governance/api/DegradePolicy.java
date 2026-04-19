package io.homeey.gateway.governance.api;

public record DegradePolicy(
        boolean enabled,
        int status,
        String contentType,
        String body
) {
    public static DegradePolicy disabled() {
        return new DegradePolicy(false, 503, "text/plain; charset=UTF-8", "service degraded");
    }

    public DegradePolicy {
        contentType = contentType == null || contentType.isBlank() ? "text/plain; charset=UTF-8" : contentType;
        body = body == null ? "" : body;
    }
}
