package dev.sakus.geyseredu.fabric;

import dev.sakus.geyseredu.common.session.SessionRecord;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FabricSessionStore {
    private final SecureRandom random = new SecureRandom();
    private final Map<String, SessionRecord> sessions = new HashMap<>();

    public SessionRecord issue(String tenantId, String issuer, long ttlMinutes) {
        pruneExpired();
        String id;
        do {
            id = randomId();
        } while (sessions.containsKey(id));

        SessionRecord session = new SessionRecord(id, tenantId, issuer, Instant.now().plusSeconds(ttlMinutes * 60));
        sessions.put(id, session);
        return session;
    }

    public boolean claim(String id, UUID playerId) {
        Optional<SessionRecord> session = find(id);
        if (session.isEmpty() || !session.get().active()) {
            return false;
        }
        session.get().claim(playerId);
        return true;
    }

    public void grantRemote(String id, String tenantId, String issuer, Instant expiresAt, UUID playerId) {
        SessionRecord session = new SessionRecord(id, tenantId, issuer, expiresAt);
        session.claim(playerId);
        sessions.put(id, session);
    }

    public Optional<SessionRecord> find(String id) {
        return Optional.ofNullable(sessions.get(id));
    }

    public Optional<SessionRecord> findActiveClaim(UUID playerId) {
        return sessions.values()
            .stream()
            .filter(SessionRecord::active)
            .filter(session -> session.isClaimedBy(playerId))
            .findFirst();
    }

    public boolean revoke(String id) {
        Optional<SessionRecord> session = find(id);
        if (session.isEmpty()) {
            return false;
        }
        session.get().revoke();
        return true;
    }

    public Collection<SessionRecord> activeSessions() {
        pruneExpired();
        return sessions.values().stream().filter(SessionRecord::active).toList();
    }

    private void pruneExpired() {
        sessions.values().removeIf(session -> !session.active() && !session.revoked());
    }

    private String randomId() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
