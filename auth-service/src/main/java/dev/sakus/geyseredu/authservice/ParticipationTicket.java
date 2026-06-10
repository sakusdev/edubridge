package dev.sakus.geyseredu.authservice;

import java.time.Instant;

public record ParticipationTicket(
    String idHash,
    String tenantId,
    String subject,
    Instant expiresAt
) {
    public boolean expired() {
        return !Instant.now().isBefore(expiresAt);
    }
}
