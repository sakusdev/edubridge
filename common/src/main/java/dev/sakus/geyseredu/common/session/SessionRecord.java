package dev.sakus.geyseredu.common.session;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class SessionRecord {
    private final String id;
    private final String tenantId;
    private final String issuer;
    private final Instant expiresAt;
    private final Set<UUID> claimedPlayers;
    private boolean revoked;

    public SessionRecord(String id, String tenantId, String issuer, Instant expiresAt) {
        this(id, tenantId, issuer, expiresAt, new HashSet<>(), false);
    }

    public SessionRecord(
        String id,
        String tenantId,
        String issuer,
        Instant expiresAt,
        Set<UUID> claimedPlayers,
        boolean revoked
    ) {
        this.id = id;
        this.tenantId = tenantId.toLowerCase(Locale.ROOT);
        this.issuer = issuer;
        this.expiresAt = expiresAt;
        this.claimedPlayers = claimedPlayers;
        this.revoked = revoked;
    }

    public String id() {
        return id;
    }

    public String tenantId() {
        return tenantId;
    }

    public String issuer() {
        return issuer;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean revoked() {
        return revoked;
    }

    public void revoke() {
        revoked = true;
    }

    public boolean active() {
        return !revoked && Instant.now().isBefore(expiresAt);
    }

    public boolean isClaimedBy(UUID playerId) {
        return claimedPlayers.contains(playerId);
    }

    public void claim(UUID playerId) {
        claimedPlayers.add(playerId);
    }

    public Set<UUID> claimedPlayers() {
        return Set.copyOf(claimedPlayers);
    }
}
