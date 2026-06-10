package dev.sakus.geyseredu.session;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SessionStore {
    private final JavaPlugin plugin;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, EduSession> sessions = new HashMap<>();
    private File file;

    public SessionStore(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        file = new File(plugin.getDataFolder(), "sessions.yml");
        sessions.clear();

        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("sessions");
        if (root == null) {
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }

            var claimedPlayers = new HashSet<UUID>();
            for (String value : section.getStringList("claimed-players")) {
                try {
                    claimedPlayers.add(UUID.fromString(value));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ignoring invalid player UUID in session " + id + ": " + value);
                }
            }

            EduSession session = new EduSession(
                id,
                section.getString("tenant-id", ""),
                section.getString("issuer", "unknown"),
                Instant.ofEpochMilli(section.getLong("expires-at")),
                claimedPlayers,
                section.getBoolean("revoked")
            );
            sessions.put(id, session);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("sessions");

        for (EduSession session : sessions.values()) {
            ConfigurationSection section = root.createSection(session.id());
            section.set("tenant-id", session.tenantId());
            section.set("issuer", session.issuer());
            section.set("expires-at", session.expiresAt().toEpochMilli());
            section.set("revoked", session.revoked());
            section.set(
                "claimed-players",
                session.claimedPlayers().stream().map(UUID::toString).sorted().toList()
            );
        }

        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save sessions.yml: " + ex.getMessage());
        }
    }

    public EduSession issue(String tenantId, String issuer, long ttlMinutes) {
        pruneExpired();
        int maxActive = plugin.getConfig().getInt("sessions.max-active-sessions", 1000);
        if (activeSessions().size() >= maxActive) {
            throw new IllegalStateException("Maximum active sessions reached.");
        }

        String id;
        do {
            id = randomId();
        } while (sessions.containsKey(id));

        EduSession session = new EduSession(
            id,
            tenantId.toLowerCase(Locale.ROOT),
            issuer,
            Instant.now().plusSeconds(ttlMinutes * 60)
        );
        sessions.put(id, session);
        save();
        return session;
    }

    public Optional<EduSession> find(String id) {
        return Optional.ofNullable(sessions.get(id));
    }

    public Optional<EduSession> findActiveClaim(UUID playerId) {
        return sessions.values()
            .stream()
            .filter(EduSession::active)
            .filter(session -> session.isClaimedBy(playerId))
            .findFirst();
    }

    public boolean claim(String id, UUID playerId) {
        Optional<EduSession> session = find(id);
        if (session.isEmpty() || !session.get().active()) {
            return false;
        }
        session.get().claim(playerId);
        save();
        return true;
    }

    public void grantRemote(String id, String tenantId, String issuer, Instant expiresAt, UUID playerId) {
        EduSession session = new EduSession(id, tenantId, issuer, expiresAt);
        session.claim(playerId);
        sessions.put(id, session);
        save();
    }

    public boolean revoke(String id) {
        Optional<EduSession> session = find(id);
        if (session.isEmpty()) {
            return false;
        }
        session.get().revoke();
        save();
        return true;
    }

    public Collection<EduSession> activeSessions() {
        pruneExpired();
        return sessions.values().stream().filter(EduSession::active).toList();
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
