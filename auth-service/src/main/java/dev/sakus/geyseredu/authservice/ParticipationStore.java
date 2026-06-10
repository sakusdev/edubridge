package dev.sakus.geyseredu.authservice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ParticipationStore {
    IssuedParticipation issue(String tenantId, String subject, Instant expiresAt);

    Optional<ParticipationTicket> consume(String participationId);

    boolean revoke(String participationId);

    boolean revokeHash(String idHash);

    List<ParticipationTicket> activeTickets();

    record IssuedParticipation(String plainId, ParticipationTicket ticket) {
    }
}
