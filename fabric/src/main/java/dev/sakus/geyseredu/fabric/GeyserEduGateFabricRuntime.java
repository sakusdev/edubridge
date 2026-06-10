package dev.sakus.geyseredu.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.sakus.geyseredu.common.compat.ClientDescriptor;
import dev.sakus.geyseredu.common.compat.CompatibilityDecision;
import dev.sakus.geyseredu.common.compat.CompatibilityPolicy;
import dev.sakus.geyseredu.common.compat.EducationCompatLayer;
import dev.sakus.geyseredu.common.auth.ParticipationVerificationRequest;
import dev.sakus.geyseredu.common.auth.RemoteDeviceCodeClient;
import dev.sakus.geyseredu.common.auth.RemoteParticipationVerifier;
import dev.sakus.geyseredu.common.session.SessionRecord;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class GeyserEduGateFabricRuntime {
    private static final String PREFIX = "[GeyserEdu] ";

    private final FabricConfig config = new FabricConfig();
    private final FabricSessionStore sessionStore = new FabricSessionStore();
    private final FabricFloodgateDetector floodgateDetector = new FabricFloodgateDetector();
    private final Map<UUID, Long> pendingDeadlines = new HashMap<>();
    private long ticks;

    public void initialize() {
        config.load();
        registerJoinGate();
        registerParticipationInput();
        registerTickGate();
        registerCommands();
    }

    private void registerJoinGate() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (!config.enabled() || !shouldGate(player)) {
                return;
            }
            if (sessionStore.findActiveClaim(player.getUuid()).isPresent()) {
                return;
            }

            pendingDeadlines.put(player.getUuid(), ticks + Math.max(1, config.joinGraceSeconds()) * 20L);
            if (config.deviceCodeEnabled()) {
                player.sendMessage(Text.literal(PREFIX + "Microsoft のログインコードを発行しています..."));
                startDeviceCodeLogin(player);
            } else {
                player.sendMessage(Text.literal(PREFIX + "参加IDをチャットに入力してください。入力した参加IDは他のプレイヤーには表示されません。"));
            }
        });
    }

    private void registerParticipationInput() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, player, parameters) -> {
            if (!pendingDeadlines.containsKey(player.getUuid())) {
                return true;
            }

            consumeParticipationId(player, message.getSignedContent().trim());
            return false;
        });
    }

    private void registerTickGate() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ticks++;
            pendingDeadlines.entrySet().removeIf(entry -> {
                if (entry.getValue() > ticks) {
                    return false;
                }

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player == null || sessionStore.findActiveClaim(player.getUuid()).isPresent()) {
                    return true;
                }

                player.networkHandler.disconnect(Text.literal(PREFIX + "Minecraft Education セッションが確認できませんでした。"));
                return true;
            });
        });
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            literal("edu-session")
                .then(literal("issue")
                    .requires(source -> source.hasPermissionLevel(3))
                    .then(argument("tenantId", StringArgumentType.word())
                        .executes(context -> issue(
                            context.getSource(),
                            StringArgumentType.getString(context, "tenantId"),
                            config.defaultTtlMinutes()
                        ))
                        .then(argument("ttlMinutes", StringArgumentType.word())
                            .executes(context -> issue(
                                context.getSource(),
                                StringArgumentType.getString(context, "tenantId"),
                                parseTtl(StringArgumentType.getString(context, "ttlMinutes"), config.defaultTtlMinutes())
                            )))))
                .then(literal("revoke")
                    .requires(source -> source.hasPermissionLevel(3))
                    .then(argument("participationId", StringArgumentType.word())
                        .executes(context -> revoke(context.getSource(), StringArgumentType.getString(context, "participationId")))))
                .then(literal("list")
                    .requires(source -> source.hasPermissionLevel(3))
                    .executes(context -> list(context.getSource())))
                .then(literal("reload")
                    .requires(source -> source.hasPermissionLevel(3))
                    .executes(context -> reload(context.getSource())))
                .then(literal("login")
                    .executes(context -> login(context.getSource())))
        ));
    }

    private void consumeParticipationId(ServerPlayerEntity player, String participationId) {
        if (!pendingDeadlines.containsKey(player.getUuid())) {
            return;
        }

        if (sessionStore.claim(participationId, player.getUuid())) {
            pendingDeadlines.remove(player.getUuid());
            player.sendMessage(Text.literal(PREFIX + "参加IDを確認しました。"));
            return;
        }

        if (!config.authServiceEnabled()) {
            player.sendMessage(Text.literal(PREFIX + "参加IDが存在しないか、有効期限切れです。もう一度入力してください。"));
            return;
        }

        player.sendMessage(Text.literal(PREFIX + "参加IDを確認中です..."));
        UUID playerId = player.getUuid();
        String playerName = player.getGameProfile().getName();
        var server = player.getServer();
        CompletableFuture.runAsync(() -> verifyRemote(server, playerId, playerName, participationId));
    }

    private void verifyRemote(net.minecraft.server.MinecraftServer server, UUID playerId, String playerName, String participationId) {
        try {
            var verifier = new RemoteParticipationVerifier(
                URI.create(config.authServiceVerifyUrl()),
                config.authServiceBearerToken(),
                Duration.ofMillis(config.authServiceTimeoutMillis())
            );
            var result = verifier.verify(new ParticipationVerificationRequest(participationId, playerId, playerName, "fabric"));

            server.execute(() -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player == null || !pendingDeadlines.containsKey(playerId)) {
                    return;
                }
                if (result.valid() && isTenantAllowed(result.tenantId())) {
                    Instant expiresAt = result.expiresAt().orElseGet(() -> Instant.now().plusSeconds(3600));
                    sessionStore.grantRemote(participationId, result.tenantId(), result.subject(), expiresAt, playerId);
                    pendingDeadlines.remove(playerId);
                    player.sendMessage(Text.literal(PREFIX + "参加IDを確認しました。"));
                } else {
                    player.sendMessage(Text.literal(PREFIX + "参加IDを確認できません: " + result.message()));
                }
            });
        } catch (Exception ex) {
            server.execute(() -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null && pendingDeadlines.containsKey(playerId)) {
                    player.sendMessage(Text.literal(PREFIX + "auth-service に接続できません: " + ex.getMessage()));
                }
            });
        }
    }

    private void startDeviceCodeLogin(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        String playerName = player.getGameProfile().getName();
        var server = player.getServer();
        CompletableFuture.runAsync(() -> {
            try {
                var result = deviceCodeClient().start(playerId.toString(), playerName, "fabric");
                server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current == null || !pendingDeadlines.containsKey(playerId)) {
                        return;
                    }
                    if (!result.started()) {
                        current.sendMessage(Text.literal(PREFIX + "Microsoft ログインコードを発行できません: " + result.message()));
                        return;
                    }
                    current.sendMessage(Text.literal(PREFIX + result.verificationUri() + " を開いてください。"));
                    current.sendMessage(Text.literal(PREFIX + "コード: " + result.userCode()));
                    current.sendMessage(Text.literal(PREFIX + "ログイン完了を自動確認しています..."));
                    scheduleDevicePoll(server, playerId, result.requestId(), Math.max(5, result.intervalSeconds()));
                });
            } catch (Exception ex) {
                server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current != null && pendingDeadlines.containsKey(playerId)) {
                        current.sendMessage(Text.literal(PREFIX + "auth-service に接続できません: " + ex.getMessage()));
                    }
                });
            }
        });
    }

    private void scheduleDevicePoll(net.minecraft.server.MinecraftServer server, UUID playerId, String requestId, int intervalSeconds) {
        long runAt = ticks + Math.max(5, intervalSeconds) * 20L;
        CompletableFuture.runAsync(() -> {
            while (ticks < runAt) {
                try {
                    Thread.sleep(250L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            pollDeviceLogin(server, playerId, requestId, intervalSeconds);
        });
    }

    private void pollDeviceLogin(net.minecraft.server.MinecraftServer server, UUID playerId, String requestId, int intervalSeconds) {
        try {
            var result = deviceCodeClient().poll(requestId, playerId.toString());
            server.execute(() -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player == null || !pendingDeadlines.containsKey(playerId)) {
                    return;
                }
                if (result.valid() && isTenantAllowed(result.tenantId())) {
                    Instant expiresAt = result.expiresAt().orElseGet(() -> Instant.now().plusSeconds(3600));
                    sessionStore.grantRemote(requestId, result.tenantId(), result.subject(), expiresAt, playerId);
                    pendingDeadlines.remove(playerId);
                    player.sendMessage(Text.literal(PREFIX + "Microsoft ログインを確認しました。"));
                    return;
                }
                if ("pending".equals(result.status())) {
                    scheduleDevicePoll(server, playerId, requestId, intervalSeconds);
                    return;
                }
                player.sendMessage(Text.literal(PREFIX + "Microsoft ログインを確認できません: " + result.message()));
            });
        } catch (Exception ex) {
            server.execute(() -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player != null && pendingDeadlines.containsKey(playerId)) {
                    player.sendMessage(Text.literal(PREFIX + "auth-service に接続できません: " + ex.getMessage()));
                    scheduleDevicePoll(server, playerId, requestId, intervalSeconds);
                }
            });
        }
    }

    private RemoteDeviceCodeClient deviceCodeClient() {
        return new RemoteDeviceCodeClient(
            URI.create(config.authServiceDeviceStartUrl()),
            URI.create(config.authServiceDevicePollUrl()),
            config.authServiceBearerToken(),
            Duration.ofMillis(config.authServiceTimeoutMillis())
        );
    }

    private int issue(ServerCommandSource source, String tenantId, long ttlMinutes) {
        if (!config.localSessionIssuerEnabled()) {
            source.sendError(Text.literal(PREFIX + "ローカルセッション発行は無効です。"));
            return 0;
        }
        if (ttlMinutes <= 0) {
            source.sendError(Text.literal("ttlMinutes must be greater than zero."));
            return 0;
        }
        if (!isTenantAllowed(tenantId)) {
            source.sendError(Text.literal(PREFIX + "このテナントは許可されていません。"));
            return 0;
        }

        SessionRecord session = sessionStore.issue(tenantId, source.getName(), ttlMinutes);
        source.sendFeedback(() -> Text.literal(PREFIX + "参加IDを発行しました: " + session.id()), false);
        source.sendFeedback(() -> Text.literal(PREFIX + "有効期限: " + session.expiresAt()), false);
        return 1;
    }

    private int revoke(ServerCommandSource source, String sessionId) {
        if (sessionStore.revoke(sessionId)) {
            source.sendFeedback(() -> Text.literal(PREFIX + "参加IDを失効しました。"), false);
            return 1;
        }

        source.sendError(Text.literal(PREFIX + "参加IDが見つかりません。"));
        return 0;
    }

    private int list(ServerCommandSource source) {
        if (sessionStore.activeSessions().isEmpty()) {
            source.sendFeedback(() -> Text.literal(PREFIX + "有効な参加IDはありません。"), false);
            return 1;
        }

        for (SessionRecord session : sessionStore.activeSessions()) {
            long remainingMinutes = Math.max(0, Duration.between(Instant.now(), session.expiresAt()).toMinutes());
            source.sendFeedback(() -> Text.literal("- " + session.id()
                + " tenant=" + session.tenantId()
                + " users=" + session.claimedPlayers().size()
                + " remaining=" + remainingMinutes + "m"), false);
        }
        return 1;
    }

    private int reload(ServerCommandSource source) {
        config.load();
        source.sendFeedback(() -> Text.literal(PREFIX + "設定を再読み込みしました。"), false);
        return 1;
    }

    private int login(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception ex) {
            source.sendError(Text.literal(PREFIX + "このコマンドはプレイヤーから実行してください。"));
            return 0;
        }
        pendingDeadlines.put(player.getUuid(), ticks + Math.max(1, config.joinGraceSeconds()) * 20L);
        startDeviceCodeLogin(player);
        return 1;
    }

    private boolean shouldGate(ServerPlayerEntity player) {
        boolean floodgatePlayer = floodgateDetector.isFloodgatePlayer(player.getUuid());
        CompatibilityPolicy policy = new CompatibilityPolicy(
            config.educationCompatEnabled(),
            config.requireSessionForAllFloodgatePlayers(),
            config.denyUnknownProtocol(),
            config.educationUsernamePrefixes(),
            CompatibilityPolicy.defaults().supportedProfiles()
        );
        CompatibilityDecision decision = new EducationCompatLayer(policy).evaluate(
            ClientDescriptor.unknownProtocol(player.getGameProfile().getName(), floodgatePlayer)
        );
        return decision.requiresSession();
    }

    private boolean isTenantAllowed(String tenantId) {
        if (config.allowedTenants().isEmpty()) {
            return true;
        }

        String normalizedTenantId = tenantId.toLowerCase(Locale.ROOT);
        return config.allowedTenants()
            .stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(normalizedTenantId::equals);
    }

    private long parseTtl(String value, long defaultValue) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
