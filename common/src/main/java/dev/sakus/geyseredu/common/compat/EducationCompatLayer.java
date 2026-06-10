package dev.sakus.geyseredu.common.compat;

import java.util.Locale;

public final class EducationCompatLayer {
    private final CompatibilityPolicy policy;

    public EducationCompatLayer(CompatibilityPolicy policy) {
        this.policy = policy;
    }

    public CompatibilityDecision evaluate(ClientDescriptor client) {
        if (!policy.enabled()) {
            return new CompatibilityDecision(CompatibilityAction.ALLOW, "compatibility layer disabled");
        }

        if (client.bedrockProtocolVersion().isPresent()) {
            int protocol = client.bedrockProtocolVersion().getAsInt();
            boolean supported = policy.supportedProfiles()
                .stream()
                .anyMatch(profile -> profile.supportsProtocol(protocol));
            if (!supported) {
                CompatibilityAction action = policy.denyUnknownEducationProtocol()
                    ? CompatibilityAction.DENY
                    : CompatibilityAction.WARN;
                return new CompatibilityDecision(action, "unsupported or unknown Education protocol: " + protocol);
            }
        }

        if (policy.requireSessionForAllFloodgatePlayers() && client.floodgatePlayer()) {
            return new CompatibilityDecision(
                CompatibilityAction.REQUIRE_SESSION,
                "all Floodgate players require an Education session"
            );
        }

        String playerName = client.playerName().toLowerCase(Locale.ROOT);
        for (String prefix : policy.educationUsernamePrefixes()) {
            if (!prefix.isBlank() && playerName.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return new CompatibilityDecision(
                    CompatibilityAction.REQUIRE_SESSION,
                    "matched Education username prefix"
                );
            }
        }

        return new CompatibilityDecision(CompatibilityAction.ALLOW, "no Education compatibility rule matched");
    }
}
