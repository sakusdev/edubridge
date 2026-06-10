package dev.sakus.geyseredu.common.compat;

import java.util.List;

public record CompatibilityPolicy(
    boolean enabled,
    boolean requireSessionForAllFloodgatePlayers,
    boolean denyUnknownEducationProtocol,
    List<String> educationUsernamePrefixes,
    List<EducationProtocolProfile> supportedProfiles
) {
    public static CompatibilityPolicy defaults() {
        return new CompatibilityPolicy(
            true,
            false,
            false,
            List.of("edu_"),
            EducationProtocolProfile.defaults()
        );
    }
}
