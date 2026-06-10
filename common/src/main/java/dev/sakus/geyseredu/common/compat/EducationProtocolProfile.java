package dev.sakus.geyseredu.common.compat;

import java.util.List;

public record EducationProtocolProfile(
    String educationVersion,
    String bedrockBaseRange,
    int minBedrockProtocol,
    int maxBedrockProtocol,
    String notes
) {
    public boolean supportsProtocol(int protocolVersion) {
        return protocolVersion >= minBedrockProtocol && protocolVersion <= maxBedrockProtocol;
    }

    public static List<EducationProtocolProfile> defaults() {
        return List.of(
            new EducationProtocolProfile(
                "1.21.133",
                "Bedrock 1.21.110-1.21.130 feature range",
                0,
                Integer.MAX_VALUE,
                "Protocol numbers are intentionally permissive until Geyser exposes exact Education client protocol metadata."
            )
        );
    }
}
