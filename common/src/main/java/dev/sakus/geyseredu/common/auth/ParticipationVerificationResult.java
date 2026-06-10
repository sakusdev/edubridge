package dev.sakus.geyseredu.common.auth;

import java.time.Instant;
import java.util.Optional;

public record ParticipationVerificationResult(
    boolean valid,
    String tenantId,
    String subject,
    Optional<Instant> expiresAt,
    String message
) {
    public static ParticipationVerificationResult invalid(String message) {
        return new ParticipationVerificationResult(false, "", "", Optional.empty(), message);
    }
}
