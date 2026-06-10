package dev.sakus.geyseredu.common.compat;

public record CompatibilityDecision(
    CompatibilityAction action,
    String reason
) {
    public boolean requiresSession() {
        return action == CompatibilityAction.REQUIRE_SESSION;
    }

    public boolean denied() {
        return action == CompatibilityAction.DENY;
    }
}
