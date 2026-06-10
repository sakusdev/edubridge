package dev.sakus.geyseredu.listener;

import dev.sakus.geyseredu.common.compat.ClientDescriptor;
import dev.sakus.geyseredu.common.compat.CompatibilityDecision;
import dev.sakus.geyseredu.common.compat.CompatibilityPolicy;
import dev.sakus.geyseredu.common.compat.EducationCompatLayer;
import dev.sakus.geyseredu.common.auth.ParticipationVerificationRequest;
import dev.sakus.geyseredu.common.auth.RemoteDeviceCodeClient;
import dev.sakus.geyseredu.common.auth.RemoteParticipationVerifier;
import dev.sakus.geyseredu.floodgate.FloodgateDetector;
import dev.sakus.geyseredu.session.SessionStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;

public final class SessionGateListener implements Listener {
    private final JavaPlugin plugin;
    private final SessionStore sessionStore;
    private final FloodgateDetector floodgateDetector;
    private final Set<UUID> pendingPlayers = ConcurrentHashMap.newKeySet();

    public SessionGateListener(JavaPlugin plugin, SessionStore sessionStore, FloodgateDetector floodgateDetector) {
        this.plugin = plugin;
        this.sessionStore = sessionStore;
        this.floodgateDetector = floodgateDetector;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("enabled", true) || player.hasPermission("geyseredu.bypass")) {
            return;
        }
        if (!shouldGate(player)) {
            return;
        }
        if (sessionStore.findActiveClaim(player.getUniqueId()).isPresent()) {
            return;
        }

        int graceSeconds = plugin.getConfig().getInt("sessions.join-grace-seconds", 45);
        String prefix = plugin.getConfig().getString("message-prefix", "[GeyserEdu]");
        pendingPlayers.add(player.getUniqueId());
        if (deviceCodeEnabled()) {
            player.sendMessage(ChatColor.YELLOW + prefix + " Microsoft のログインコードを発行しています...");
            startDeviceCodeLogin(player);
        } else {
            player.sendMessage(ChatColor.YELLOW + prefix + " 参加IDをチャットに入力してください。入力した参加IDは他のプレイヤーには表示されません。");
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || player.hasPermission("geyseredu.bypass")) {
                return;
            }
            if (sessionStore.findActiveClaim(player.getUniqueId()).isPresent()) {
                pendingPlayers.remove(player.getUniqueId());
                return;
            }
            pendingPlayers.remove(player.getUniqueId());
            player.kickPlayer(prefix + " Minecraft Education セッションが確認できませんでした。");
        }, Math.max(1, graceSeconds) * 20L);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!pendingPlayers.contains(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        String participationId = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> consumeParticipationId(player, participationId));
    }

    private void consumeParticipationId(Player player, String participationId) {
        String prefix = plugin.getConfig().getString("message-prefix", "[GeyserEdu]");
        if (!player.isOnline() || !pendingPlayers.contains(player.getUniqueId())) {
            return;
        }

        if (sessionStore.claim(participationId, player.getUniqueId())) {
            pendingPlayers.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + prefix + " 参加IDを確認しました。");
            return;
        }

        if (!plugin.getConfig().getBoolean("auth-service.enabled", false)) {
            player.sendMessage(ChatColor.RED + prefix + " 参加IDが存在しないか、有効期限切れです。もう一度入力してください。");
            return;
        }

        player.sendMessage(ChatColor.GRAY + prefix + " 参加IDを確認中です...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> verifyRemote(player.getUniqueId(), player.getName(), participationId));
    }

    private void startDeviceCodeLogin(Player player) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        String prefix = plugin.getConfig().getString("message-prefix", "[GeyserEdu]");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                RemoteDeviceCodeClient client = deviceCodeClient();
                var result = client.start(playerId.toString(), playerName, "paper");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player current = Bukkit.getPlayer(playerId);
                    if (current == null || !current.isOnline() || !pendingPlayers.contains(playerId)) {
                        return;
                    }
                    if (!result.started()) {
                        current.sendMessage(ChatColor.RED + prefix + " Microsoft ログインコードを発行できません: " + result.message());
                        return;
                    }
                    current.sendMessage(ChatColor.AQUA + prefix + " " + result.verificationUri() + " を開いてください。");
                    current.sendMessage(ChatColor.AQUA + prefix + " コード: " + ChatColor.WHITE + result.userCode());
                    current.sendMessage(ChatColor.GRAY + prefix + " ログイン完了を自動確認しています...");
                    scheduleDevicePoll(playerId, result.requestId(), Math.max(5, result.intervalSeconds()));
                });
            } catch (Exception ex) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player current = Bukkit.getPlayer(playerId);
                    if (current != null && current.isOnline() && pendingPlayers.contains(playerId)) {
                        current.sendMessage(ChatColor.RED + prefix + " auth-service に接続できません: " + ex.getMessage());
                    }
                });
            }
        });
    }

    private void scheduleDevicePoll(UUID playerId, String requestId, int intervalSeconds) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || !pendingPlayers.contains(playerId)) {
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> pollDeviceLogin(playerId, requestId, intervalSeconds));
        }, Math.max(5, intervalSeconds) * 20L);
    }

    private void pollDeviceLogin(UUID playerId, String requestId, int intervalSeconds) {
        String prefix = plugin.getConfig().getString("message-prefix", "[GeyserEdu]");
        try {
            var result = deviceCodeClient().poll(requestId, playerId.toString());
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline() || !pendingPlayers.contains(playerId)) {
                    return;
                }
                if (result.valid() && isTenantAllowed(result.tenantId())) {
                    Instant expiresAt = result.expiresAt().orElseGet(() -> Instant.now().plusSeconds(3600));
                    sessionStore.grantRemote(requestId, result.tenantId(), result.subject(), expiresAt, playerId);
                    pendingPlayers.remove(playerId);
                    player.sendMessage(ChatColor.GREEN + prefix + " Microsoft ログインを確認しました。");
                    return;
                }
                if ("pending".equals(result.status())) {
                    scheduleDevicePoll(playerId, requestId, intervalSeconds);
                    return;
                }
                player.sendMessage(ChatColor.RED + prefix + " Microsoft ログインを確認できません: " + result.message());
            });
        } catch (Exception ex) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline() && pendingPlayers.contains(playerId)) {
                    player.sendMessage(ChatColor.RED + prefix + " auth-service に接続できません: " + ex.getMessage());
                    scheduleDevicePoll(playerId, requestId, intervalSeconds);
                }
            });
        }
    }

    private boolean deviceCodeEnabled() {
        return plugin.getConfig().getBoolean("auth-service.enabled", false)
            && plugin.getConfig().getBoolean("auth-service.device-code.enabled", false);
    }

    private RemoteDeviceCodeClient deviceCodeClient() {
        Duration timeout = Duration.ofMillis(plugin.getConfig().getLong("auth-service.timeout-millis", 5000));
        return new RemoteDeviceCodeClient(
            URI.create(plugin.getConfig().getString("auth-service.device-code.start-url", "")),
            URI.create(plugin.getConfig().getString("auth-service.device-code.poll-url", "")),
            plugin.getConfig().getString("auth-service.bearer-token", ""),
            timeout
        );
    }

    private void verifyRemote(UUID playerId, String playerName, String participationId) {
        String prefix = plugin.getConfig().getString("message-prefix", "[GeyserEdu]");
        try {
            URI verifyUri = URI.create(plugin.getConfig().getString("auth-service.verify-url", ""));
            String token = plugin.getConfig().getString("auth-service.bearer-token", "");
            Duration timeout = Duration.ofMillis(plugin.getConfig().getLong("auth-service.timeout-millis", 5000));
            var verifier = new RemoteParticipationVerifier(verifyUri, token, timeout);
            var result = verifier.verify(new ParticipationVerificationRequest(participationId, playerId, playerName, "paper"));

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline() || !pendingPlayers.contains(playerId)) {
                    return;
                }

                if (result.valid() && isTenantAllowed(result.tenantId())) {
                    Instant expiresAt = result.expiresAt().orElseGet(() -> Instant.now().plusSeconds(3600));
                    sessionStore.grantRemote(participationId, result.tenantId(), result.subject(), expiresAt, playerId);
                    pendingPlayers.remove(playerId);
                    player.sendMessage(ChatColor.GREEN + prefix + " 参加IDを確認しました。");
                } else {
                    player.sendMessage(ChatColor.RED + prefix + " 参加IDを確認できません: " + result.message());
                }
            });
        } catch (Exception ex) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline() && pendingPlayers.contains(playerId)) {
                    player.sendMessage(ChatColor.RED + prefix + " auth-service に接続できません: " + ex.getMessage());
                }
            });
        }
    }

    private boolean isTenantAllowed(String tenantId) {
        var allowedTenants = plugin.getConfig().getStringList("allowed-tenants");
        if (allowedTenants.isEmpty()) {
            return true;
        }
        return allowedTenants.stream().anyMatch(tenant -> tenant.equalsIgnoreCase(tenantId));
    }

    private boolean shouldGate(Player player) {
        boolean floodgatePlayer = floodgateDetector.isFloodgatePlayer(player.getUniqueId());
        CompatibilityPolicy policy = new CompatibilityPolicy(
            plugin.getConfig().getBoolean("education-compat.enabled", true),
            plugin.getConfig().getBoolean("require-session-for-all-floodgate-players", false),
            plugin.getConfig().getBoolean("education-compat.deny-unknown-protocol", false),
            plugin.getConfig().getStringList("education-username-prefixes"),
            CompatibilityPolicy.defaults().supportedProfiles()
        );
        CompatibilityDecision decision = new EducationCompatLayer(policy).evaluate(
            ClientDescriptor.unknownProtocol(player.getName(), floodgatePlayer)
        );
        return decision.requiresSession();
    }
}
