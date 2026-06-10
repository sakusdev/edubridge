package dev.sakus.geyseredu.common.auth;

import java.util.UUID;

public record ParticipationVerificationRequest(
    String participationId,
    UUID playerUuid,
    String playerName,
    String platform
) {
}
