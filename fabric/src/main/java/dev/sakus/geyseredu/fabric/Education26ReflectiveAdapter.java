package dev.sakus.geyseredu.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.sakus.geyseredu.common.auth.RemoteDeviceCodeClient;
import dev.sakus.geyseredu.common.session.SessionRecord;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;

public final class Education26ReflectiveAdapter {
    private static final String PREFIX = "[GeyserEdu] ";

    private final FabricConfig config = new FabricConfig();
    private final FabricEmbeddedAuthService embeddedAuthService = new FabricEmbeddedAuthService();
    private final FabricSessionStore sessionStore = new FabricSessionStore();

    public void initialize() {
        config.load();
        embeddedAuthService.startIfEnabled(config);
        registerCommands();
        System.err.println("[GeyserEdu] Education 26.x reflective adapter enabled. Join/chat gating is not active yet; local /edu-session commands are available.");
    }

    private void registerCommands() {
        try {
            Class<?> callbackClass = Class.forName("net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback");
            Field eventField = callbackClass.getField("EVENT");
            Object event = eventField.get(null);
            Object listener = Proxy.newProxyInstance(
                callbackClass.getClassLoader(),
                new Class<?>[]{callbackClass},
                commandRegistrationHandler()
            );
            Method register = findSingleArgumentMethod(event.getClass(), "register");
            register.invoke(event, listener);
        } catch (ClassNotFoundException ex) {
            System.err.println("[GeyserEdu] Fabric API command v2 is missing; /edu-session cannot be registered on Education 26.x.");
        } catch (ReflectiveOperationException ex) {
            System.err.println("[GeyserEdu] Failed to register Education 26.x reflective commands: " + ex);
        }
    }

    private InvocationHandler commandRegistrationHandler() {
        return (proxy, method, args) -> {
            if (args == null || args.length == 0 || !"register".equals(method.getName())) {
                return null;
            }
            registerCommandTree(args[0]);
            return null;
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerCommandTree(Object dispatcher) throws ReflectiveOperationException {
        LiteralArgumentBuilder root = LiteralArgumentBuilder.literal("edu-session")
            .executes(context -> status((CommandContext<?>) context))
            .then(LiteralArgumentBuilder.literal("status")
                .executes(context -> status((CommandContext<?>) context)))
            .then(LiteralArgumentBuilder.literal("issue")
                .then(argument("tenantId", StringArgumentType.word())
                    .executes(context -> issue((CommandContext<?>) context, StringArgumentType.getString(context, "tenantId"), config.defaultTtlMinutes()))
                    .then(argument("ttlMinutes", StringArgumentType.word())
                        .executes(context -> issue(
                            (CommandContext<?>) context,
                            StringArgumentType.getString(context, "tenantId"),
                            parseTtl(StringArgumentType.getString(context, "ttlMinutes"), config.defaultTtlMinutes())
                        )))))
            .then(LiteralArgumentBuilder.literal("list")
                .executes(context -> list((CommandContext<?>) context)))
            .then(LiteralArgumentBuilder.literal("revoke")
                .then(argument("participationId", StringArgumentType.word())
                    .executes(context -> revoke((CommandContext<?>) context, StringArgumentType.getString(context, "participationId")))))
            .then(LiteralArgumentBuilder.literal("reload")
                .executes(context -> reload((CommandContext<?>) context)))
            .then(LiteralArgumentBuilder.literal("login")
                .executes(context -> login((CommandContext<?>) context)));

        dispatcher.getClass().getMethod("register", LiteralArgumentBuilder.class).invoke(dispatcher, root);
    }

    private int status(CommandContext<?> context) {
        sendFeedback(context.getSource(), PREFIX + "Education 26.x adapter is loaded. Local participation ID commands are available; join/chat gating is not active yet.");
        return 1;
    }

    private int issue(CommandContext<?> context, String tenantId, long ttlMinutes) {
        Object source = context.getSource();
        if (!config.localSessionIssuerEnabled()) {
            sendError(source, PREFIX + "ローカルセッション発行は無効です。");
            return 0;
        }
        if (ttlMinutes <= 0) {
            sendError(source, "ttlMinutes must be greater than zero.");
            return 0;
        }
        if (!isTenantAllowed(tenantId)) {
            sendError(source, PREFIX + "このテナントは許可されていません。");
            return 0;
        }

        SessionRecord session = sessionStore.issue(tenantId, sourceName(source), ttlMinutes);
        sendFeedback(source, PREFIX + "参加IDを発行しました: " + session.id());
        sendFeedback(source, PREFIX + "有効期限: " + session.expiresAt());
        return 1;
    }

    private int list(CommandContext<?> context) {
        Object source = context.getSource();
        if (sessionStore.activeSessions().isEmpty()) {
            sendFeedback(source, PREFIX + "有効な参加IDはありません。");
            return 1;
        }

        for (SessionRecord session : sessionStore.activeSessions()) {
            long remainingMinutes = Math.max(0, Duration.between(Instant.now(), session.expiresAt()).toMinutes());
            sendFeedback(source, "- " + session.id()
                + " tenant=" + session.tenantId()
                + " users=" + session.claimedPlayers().size()
                + " remaining=" + remainingMinutes + "m");
        }
        return 1;
    }

    private int revoke(CommandContext<?> context, String participationId) {
        Object source = context.getSource();
        if (sessionStore.revoke(participationId)) {
            sendFeedback(source, PREFIX + "参加IDを失効しました。");
            return 1;
        }

        sendError(source, PREFIX + "参加IDが見つかりません。");
        return 0;
    }

    private int reload(CommandContext<?> context) {
        config.load();
        sendFeedback(context.getSource(), PREFIX + "設定を再読み込みしました。");
        return 1;
    }

    private int login(CommandContext<?> context) {
        Object source = context.getSource();
        if (!config.authServiceEnabled() || !config.deviceCodeEnabled()) {
            sendError(source, PREFIX + "auth-service device-code が無効です。config/geyser-edu-gate.properties を確認してください。");
            return 0;
        }

        String playerName = sourceName(source);
        String playerUuid = sourceUuid(source);
        sendFeedback(source, PREFIX + "Microsoft のログインコードを発行しています...");
        CompletableFuture.runAsync(() -> runDeviceLogin(source, playerUuid, playerName));
        return 1;
    }

    private void runDeviceLogin(Object source, String playerUuid, String playerName) {
        try {
            RemoteDeviceCodeClient client = deviceCodeClient();
            var start = client.start(playerUuid, playerName, "fabric-education-26");
            if (!start.started()) {
                sendError(source, PREFIX + "Microsoft ログインコードを発行できません: " + start.message());
                return;
            }

            sendFeedback(source, PREFIX + start.verificationUri() + " を開いてください。");
            sendFeedback(source, PREFIX + "コード: " + start.userCode());
            sendFeedback(source, PREFIX + "ログイン完了を自動確認しています...");

            int intervalSeconds = Math.max(5, start.intervalSeconds());
            while (Instant.now().isBefore(start.expiresAt())) {
                sleep(intervalSeconds);
                var status = client.poll(start.requestId(), playerUuid);
                if (status.valid() && isTenantAllowed(status.tenantId())) {
                    sendFeedback(source, PREFIX + "Microsoft ログインを確認しました。tenant=" + status.tenantId());
                    return;
                }
                if (!"pending".equals(status.status())) {
                    sendError(source, PREFIX + "Microsoft ログインを確認できません: " + status.message());
                    return;
                }
            }
            sendError(source, PREFIX + "Microsoft ログインコードの有効期限が切れました。");
        } catch (Exception ex) {
            sendError(source, PREFIX + "auth-service に接続できません: " + ex.getMessage());
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

    private static long parseTtl(String value, long defaultValue) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static String sourceName(Object source) {
        for (String methodName : new String[]{"getName", "getTextName"}) {
            try {
                Object value = source.getClass().getMethod(methodName).invoke(source);
                if (value != null) {
                    return value.toString();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return "unknown";
    }

    private static String sourceUuid(Object source) {
        Object player = sourcePlayer(source);
        if (player != null) {
            for (String methodName : new String[]{"getUuid", "getUUID", "getUniqueId"}) {
                try {
                    Object value = player.getClass().getMethod(methodName).invoke(player);
                    if (value != null) {
                        return value.toString();
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
            Object profile = invokeNoArg(player, "getGameProfile");
            if (profile != null) {
                Object id = invokeNoArg(profile, "getId");
                if (id != null) {
                    return id.toString();
                }
            }
        }
        return UUID.nameUUIDFromBytes(sourceName(source).getBytes()).toString();
    }

    private static Object sourcePlayer(Object source) {
        for (String methodName : new String[]{"getPlayer", "getPlayerOrThrow", "getPlayerOrException", "getEntity"}) {
            Object value = invokeNoArg(source, methodName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sendFeedback(Object source, String message) {
        if (!sendWithOfficialComponent(source, message, false) && !sendWithNamedText(source, message, false)) {
            System.out.println(message);
        }
    }

    private static void sendError(Object source, String message) {
        if (!sendWithOfficialComponent(source, message, true) && !sendWithNamedText(source, message, true)) {
            System.err.println(message);
        }
    }

    private static boolean sendWithOfficialComponent(Object source, String message, boolean error) {
        try {
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Object component = componentClass.getMethod("literal", String.class).invoke(null, message);
            if (error) {
                Method sendFailure = findMethod(source.getClass(), "sendFailure", componentClass);
                if (sendFailure != null) {
                    sendFailure.invoke(source, component);
                    return true;
                }
            }
            Method sendSuccess = findMethod(source.getClass(), "sendSuccess", Supplier.class, boolean.class);
            if (sendSuccess != null) {
                Supplier<Object> supplier = () -> component;
                sendSuccess.invoke(source, supplier, false);
                return true;
            }
            Method sendSystemMessage = findMethod(source.getClass(), "sendSystemMessage", componentClass);
            if (sendSystemMessage != null) {
                sendSystemMessage.invoke(source, component);
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }

    private static boolean sendWithNamedText(Object source, String message, boolean error) {
        try {
            Class<?> textClass = Class.forName("net.minecraft.text.Text");
            Object text = textClass.getMethod("literal", String.class).invoke(null, message);
            if (error) {
                Method sendError = findMethod(source.getClass(), "sendError", textClass);
                if (sendError != null) {
                    sendError.invoke(source, text);
                    return true;
                }
            }
            Method sendFeedback = findMethod(source.getClass(), "sendFeedback", Supplier.class, boolean.class);
            if (sendFeedback != null) {
                Supplier<Object> supplier = () -> text;
                sendFeedback.invoke(source, supplier, false);
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }

    private static Method findSingleArgumentMethod(Class<?> type, String name) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }
}
