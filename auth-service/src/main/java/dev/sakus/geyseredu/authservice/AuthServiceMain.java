package dev.sakus.geyseredu.authservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public final class AuthServiceMain {
    private static final int MAX_REQUEST_BODY_BYTES = 8192;
    private static final int MAX_PENDING_LOGIN_STATES = 2048;
    private static final Duration LOGIN_STATE_TTL = Duration.ofMinutes(10);

    private final AuthConfig config;
    private final OAuthClient oauthClient;
    private final JwksTokenValidator tokenValidator;
    private final ParticipationStore participationStore;
    private final AuditLogger auditLogger;
    private final RateLimiter verifyRateLimiter;
    private final Map<String, Instant> states = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final boolean ready;
    private final String readinessMessage;

    public AuthServiceMain(AuthConfig config) {
        this.config = config;
        this.oauthClient = new OAuthClient(config);
        this.tokenValidator = new JwksTokenValidator(config);
        this.participationStore = participationStore(config);
        this.auditLogger = new AuditLogger(config.auditLogPath());
        this.verifyRateLimiter = new RateLimiter(config.verifyRateLimitPerMinute(), 60_000);
        var validationErrors = config.validationErrors();
        this.ready = validationErrors.isEmpty();
        this.readinessMessage = validationErrors.isEmpty()
            ? "ready"
            : validationErrors.stream().collect(Collectors.joining("; "));
    }

    private static ParticipationStore participationStore(AuthConfig config) {
        ParticipationIdHasher hasher = new ParticipationIdHasher(config.participationIdHashSecret());
        if ("postgres".equalsIgnoreCase(config.storeBackend())) {
            return new PostgresParticipationStore(
                config.postgresJdbcUrl(),
                config.postgresUsername(),
                config.postgresPassword(),
                hasher
            );
        }
        return new FileParticipationStore(config.ticketStorePath(), hasher);
    }

    public static void main(String[] args) throws IOException {
        AuthConfig config = AuthConfig.fromEnv();
        new AuthServiceMain(config).start();
    }

    public void start() throws IOException {
        startServer();
    }

    public HttpServer startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/", this::index);
        server.createContext("/health/live", this::healthLive);
        server.createContext("/health/ready", this::healthReady);
        server.createContext("/login", this::login);
        server.createContext("/callback", this::callback);
        server.createContext("/api/participation/verify", this::verify);
        server.createContext("/api/admin/participation/revoke", this::revoke);
        server.createContext("/api/admin/participation/list", this::listTickets);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Geyser Edu auth-service listening on http://127.0.0.1:" + config.port());
        if (!ready) {
            System.err.println("Geyser Edu auth-service is not ready: " + readinessMessage);
        }
        return server;
    }

    private void index(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "Method not allowed");
            return;
        }
        sendHtml(exchange, 200, """
            <html><body>
            <h1>Geyser Edu Auth</h1>
            <p><a href="/login">Microsoft Entra ID でログイン</a></p>
            </body></html>
            """);
    }

    private void login(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendHtml(exchange, 405, "<html><body>Method not allowed.</body></html>");
            return;
        }
        if (!ready) {
            sendHtml(exchange, 503, "<html><body>Service is not ready: " + JsonUtil.escape(readinessMessage) + "</body></html>");
            return;
        }
        pruneStates();
        if (states.size() >= MAX_PENDING_LOGIN_STATES) {
            sendHtml(exchange, 429, "<html><body>Too many pending login attempts. Retry later.</body></html>");
            return;
        }
        String state = randomToken();
        states.put(state, Instant.now().plus(LOGIN_STATE_TTL));
        redirect(exchange, oauthClient.authorizeUri(state));
    }

    private void callback(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendHtml(exchange, 405, "<html><body>Method not allowed.</body></html>");
            return;
        }
        if (!ready) {
            sendHtml(exchange, 503, "<html><body>Service is not ready: " + JsonUtil.escape(readinessMessage) + "</body></html>");
            return;
        }
        Map<String, String> query = query(exchange.getRequestURI());
        String code = query.getOrDefault("code", "");
        String state = query.getOrDefault("state", "");
        if (code.isBlank() || state.isBlank() || !consumeState(state)) {
            sendHtml(exchange, 400, "<html><body>Invalid login callback.</body></html>");
            return;
        }

        try {
            String idToken = oauthClient.exchangeCodeForIdToken(code);
            String payload = tokenValidator.validateAndReadPayload(idToken);
            String tenantId = JsonUtil.stringField(payload, "tid").orElse("");
            String subject = JsonUtil.stringField(payload, "oid")
                .or(() -> JsonUtil.stringField(payload, "sub"))
                .orElse("unknown");

            ParticipationStore.IssuedParticipation issued = participationStore.issue(
                tenantId,
                subject,
                Instant.now().plus(config.participationTtl())
            );
            ParticipationTicket ticket = issued.ticket();
            auditLogger.log("participation.issue", subject, tenantId, ticket.idHash());
            sendHtml(exchange, 200, """
                <html><body>
                <h1>参加ID</h1>
                <p>このIDをMinecraft Educationクライアントのチャットに入力してください。</p>
                <pre style="font-size: 24px;">%s</pre>
                <p>Expires at: %s</p>
                </body></html>
                """.formatted(JsonUtil.escape(issued.plainId()), ticket.expiresAt()));
        } catch (Exception ex) {
            auditLogger.log("login.failure", "", "", ex.getMessage());
            sendHtml(exchange, 500, "<html><body>Login failed. Check the auth-service audit log.</body></html>");
        }
    }

    private void verify(HttpExchange exchange) throws IOException {
        if (!ready) {
            sendJson(exchange, 503, "{\"valid\":false,\"message\":\"service is not ready\"}");
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"valid\":false,\"message\":\"method not allowed\"}");
            return;
        }
        if (!verifyAuthorized(exchange)) {
            sendJson(exchange, 401, "{\"valid\":false,\"message\":\"unauthorized\"}");
            return;
        }

        String body;
        try {
            body = readBody(exchange);
        } catch (RequestBodyTooLargeException ex) {
            sendJson(exchange, 413, "{\"valid\":false,\"message\":\"request body too large\"}");
            return;
        }
        String participationId = JsonUtil.stringField(body, "participationId").orElse("");
        String playerUuid = JsonUtil.stringField(body, "playerUuid").orElse("");
        String rateKey = remoteAddress(exchange) + ":" + playerUuid;
        if (!verifyRateLimiter.allow(rateKey)) {
            auditLogger.log("participation.rate_limited", playerUuid, "", remoteAddress(exchange));
            sendJson(exchange, 429, "{\"valid\":false,\"message\":\"rate limited\"}");
            return;
        }
        if (participationId.isBlank()) {
            sendJson(exchange, 400, "{\"valid\":false,\"message\":\"participationId is required\"}");
            return;
        }

        var ticket = participationStore.consume(participationId);
        if (ticket.isEmpty()) {
            auditLogger.log("participation.verify_failed", playerUuid, "", "not found or expired");
            sendJson(exchange, 404, "{\"valid\":false,\"message\":\"participation ID not found or expired\"}");
            return;
        }

        ParticipationTicket value = ticket.get();
        auditLogger.log("participation.verify_success", playerUuid, value.tenantId(), value.idHash());
        sendJson(exchange, 200, "{"
            + "\"valid\":true,"
            + "\"tenantId\":" + JsonUtil.jsonString(value.tenantId()) + ","
            + "\"subject\":" + JsonUtil.jsonString(value.subject()) + ","
            + "\"expiresAt\":" + JsonUtil.jsonString(value.expiresAt().toString()) + ","
            + "\"message\":\"verified\""
            + "}");
    }

    private void revoke(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"revoked\":false,\"message\":\"method not allowed\"}");
            return;
        }
        if (!adminAuthorized(exchange)) {
            sendJson(exchange, 401, "{\"revoked\":false,\"message\":\"unauthorized\"}");
            return;
        }

        String body;
        try {
            body = readBody(exchange);
        } catch (RequestBodyTooLargeException ex) {
            sendJson(exchange, 413, "{\"revoked\":false,\"message\":\"request body too large\"}");
            return;
        }
        String participationId = JsonUtil.stringField(body, "participationId").orElse("");
        String idHash = JsonUtil.stringField(body, "idHash").orElse("");
        if (participationId.isBlank() && idHash.isBlank()) {
            sendJson(exchange, 400, "{\"revoked\":false,\"message\":\"participationId or idHash is required\"}");
            return;
        }

        boolean revoked = participationId.isBlank()
            ? participationStore.revokeHash(idHash)
            : participationStore.revoke(participationId);
        auditLogger.log(revoked ? "participation.revoke" : "participation.revoke_missing", "", "", participationId.isBlank() ? idHash : "provided-id");
        sendJson(exchange, 200, "{\"revoked\":" + revoked + "}");
    }

    private void listTickets(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"tickets\":[],\"message\":\"method not allowed\"}");
            return;
        }
        if (!adminAuthorized(exchange)) {
            sendJson(exchange, 401, "{\"tickets\":[],\"message\":\"unauthorized\"}");
            return;
        }

        String tickets = participationStore.activeTickets()
            .stream()
            .map(ticket -> "{"
                + "\"idHash\":" + JsonUtil.jsonString(ticket.idHash()) + ","
                + "\"tenantId\":" + JsonUtil.jsonString(ticket.tenantId()) + ","
                + "\"subject\":" + JsonUtil.jsonString(ticket.subject()) + ","
                + "\"expiresAt\":" + JsonUtil.jsonString(ticket.expiresAt().toString())
                + "}")
            .collect(Collectors.joining(","));
        sendJson(exchange, 200, "{\"tickets\":[" + tickets + "]}");
    }

    private String randomToken() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean consumeState(String state) {
        Instant expiresAt = states.remove(state);
        return expiresAt != null && Instant.now().isBefore(expiresAt);
    }

    private void pruneStates() {
        Instant now = Instant.now();
        states.entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> values = new HashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return values;
        }
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
    }

    private static void redirect(HttpExchange exchange, URI uri) throws IOException {
        exchange.getResponseHeaders().set("Location", uri.toString());
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void healthLive(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, "{\"status\":\"live\"}");
    }

    private void healthReady(HttpExchange exchange) throws IOException {
        int status = ready ? 200 : 503;
        sendJson(exchange, status, "{\"ready\":" + ready + ",\"message\":" + JsonUtil.jsonString(readinessMessage) + "}");
    }

    private boolean verifyAuthorized(HttpExchange exchange) {
        if (config.verifyBearerToken().isBlank()) {
            return false;
        }
        return bearerTokenMatches(config.verifyBearerToken(), exchange.getRequestHeaders().getFirst("Authorization"));
    }

    private boolean adminAuthorized(HttpExchange exchange) {
        if (!config.adminBearerToken().isBlank()) {
            return bearerTokenMatches(config.adminBearerToken(), exchange.getRequestHeaders().getFirst("Authorization"));
        }
        return !config.production() && verifyAuthorized(exchange);
    }

    static boolean bearerTokenMatches(String configuredToken, String actualHeader) {
        if (configuredToken == null || configuredToken.isBlank() || actualHeader == null) {
            return false;
        }
        byte[] expected = ("Bearer " + configuredToken).getBytes(StandardCharsets.UTF_8);
        byte[] actual = actualHeader.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static String remoteAddress(HttpExchange exchange) {
        return exchange.getRemoteAddress() == null
            ? "unknown"
            : exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private static void sendHtml(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        send(exchange, status, body);
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        send(exchange, status, body);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = readLimited(exchange.getRequestBody(), MAX_REQUEST_BODY_BYTES);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        byte[] bytes = input.readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) {
            throw new RequestBodyTooLargeException();
        }
        return bytes;
    }

    private static final class RequestBodyTooLargeException extends IOException {
    }
}
