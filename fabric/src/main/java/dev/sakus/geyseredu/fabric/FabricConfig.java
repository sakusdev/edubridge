package dev.sakus.geyseredu.fabric;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class FabricConfig {
    private final Path path = FabricLoader.getInstance().getConfigDir().resolve("geyser-edu-gate.properties");
    private final Properties properties = new Properties();

    public void load() {
        setDefaults();
        if (Files.exists(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to load " + path, ex);
            }
        } else {
            save();
        }
    }

    public void save() {
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "Geyser Edu Gate Fabric configuration");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save " + path, ex);
        }
    }

    public boolean enabled() {
        return bool("enabled", true);
    }

    public boolean educationCompatEnabled() {
        return bool("education-compat.enabled", true);
    }

    public boolean requireSessionForAllFloodgatePlayers() {
        return bool("require-session-for-all-floodgate-players", false);
    }

    public boolean denyUnknownProtocol() {
        return bool("education-compat.deny-unknown-protocol", false);
    }

    public boolean localSessionIssuerEnabled() {
        return bool("local-session-issuer.enabled", true);
    }

    public boolean authServiceEnabled() {
        return bool("auth-service.enabled", false);
    }

    public boolean deviceCodeEnabled() {
        return authServiceEnabled() && bool("auth-service.device-code.enabled", true);
    }

    public boolean embeddedAuthServiceEnabled() {
        return bool("auth-service.embedded.enabled", false);
    }

    public String authServiceVerifyUrl() {
        return properties.getProperty("auth-service.verify-url", "");
    }

    public String authServiceDeviceStartUrl() {
        return properties.getProperty("auth-service.device-code.start-url", "http://127.0.0.1:8080/api/device/start");
    }

    public String authServiceDevicePollUrl() {
        return properties.getProperty("auth-service.device-code.poll-url", "http://127.0.0.1:8080/api/device/poll");
    }

    public String authServiceBearerToken() {
        return properties.getProperty("auth-service.bearer-token", "");
    }

    public long authServiceTimeoutMillis() {
        return integer("auth-service.timeout-millis", 5000);
    }

    public int joinGraceSeconds() {
        return integer("sessions.join-grace-seconds", 45);
    }

    public long defaultTtlMinutes() {
        return integer("sessions.default-ttl-minutes", 60);
    }

    public List<String> educationUsernamePrefixes() {
        return csv("education-username-prefixes", "edu_");
    }

    public List<String> allowedTenants() {
        return csv("allowed-tenants", "");
    }

    public Map<String, String> embeddedAuthValues() {
        Map<String, String> values = new HashMap<>();
        put(values, "GEYSER_EDU_ENV", properties.getProperty("auth-service.embedded.environment", "development"));
        put(values, "GEYSER_EDU_CLIENT_ID", properties.getProperty("auth-service.embedded.client-id", ""));
        put(values, "GEYSER_EDU_CLIENT_SECRET", properties.getProperty("auth-service.embedded.client-secret", ""));
        put(values, "GEYSER_EDU_TENANT", properties.getProperty("auth-service.embedded.tenant", "organizations"));
        put(values, "GEYSER_EDU_REDIRECT_URI", properties.getProperty("auth-service.embedded.redirect-uri", "http://127.0.0.1:8080/callback"));
        put(values, "GEYSER_EDU_ALLOWED_TENANTS", properties.getProperty("auth-service.embedded.allowed-tenants", ""));
        put(values, "GEYSER_EDU_VERIFY_BEARER_TOKEN", authServiceBearerToken());
        put(values, "GEYSER_EDU_ADMIN_BEARER_TOKEN", properties.getProperty("auth-service.embedded.admin-bearer-token", ""));
        put(values, "GEYSER_EDU_PARTICIPATION_ID_HASH_SECRET", properties.getProperty("auth-service.embedded.participation-id-hash-secret", ""));
        put(values, "GEYSER_EDU_PARTICIPATION_TTL_MINUTES", properties.getProperty("auth-service.embedded.participation-ttl-minutes", "10"));
        put(values, "GEYSER_EDU_STORE_BACKEND", properties.getProperty("auth-service.embedded.store-backend", "file"));
        put(values, "GEYSER_EDU_TICKET_STORE", path.getParent().resolve(properties.getProperty("auth-service.embedded.ticket-store", "geyser-edu-auth/participation-tickets.tsv")).toString());
        put(values, "GEYSER_EDU_AUDIT_LOG", path.getParent().resolve(properties.getProperty("auth-service.embedded.audit-log", "geyser-edu-auth/audit.tsv")).toString());
        put(values, "GEYSER_EDU_VERIFY_RATE_LIMIT_PER_MINUTE", properties.getProperty("auth-service.embedded.verify-rate-limit-per-minute", "60"));
        put(values, "GEYSER_EDU_AUTH_PORT", properties.getProperty("auth-service.embedded.port", "8080"));
        put(values, "GEYSER_EDU_POSTGRES_JDBC_URL", properties.getProperty("auth-service.embedded.postgres-jdbc-url", "jdbc:postgresql://postgres:5432/geyser_edu"));
        put(values, "GEYSER_EDU_POSTGRES_USERNAME", properties.getProperty("auth-service.embedded.postgres-username", "geyser_edu"));
        put(values, "GEYSER_EDU_POSTGRES_PASSWORD", properties.getProperty("auth-service.embedded.postgres-password", ""));
        return values;
    }

    private void setDefaults() {
        properties.putIfAbsent("enabled", "true");
        properties.putIfAbsent("require-session-for-all-floodgate-players", "false");
        properties.putIfAbsent("sessions.default-ttl-minutes", "60");
        properties.putIfAbsent("sessions.join-grace-seconds", "45");
        properties.putIfAbsent("education-username-prefixes", "edu_");
        properties.putIfAbsent("allowed-tenants", "");
        properties.putIfAbsent("education-compat.enabled", "true");
        properties.putIfAbsent("education-compat.deny-unknown-protocol", "false");
        properties.putIfAbsent("education-compat.profile.1.education-version", "1.21.133");
        properties.putIfAbsent("education-compat.profile.1.bedrock-base-range", "1.21.110-1.21.130");
        properties.putIfAbsent("local-session-issuer.enabled", "true");
        properties.putIfAbsent("auth-service.enabled", "false");
        properties.putIfAbsent("auth-service.verify-url", "http://127.0.0.1:8080/api/participation/verify");
        properties.putIfAbsent("auth-service.embedded.enabled", "false");
        properties.putIfAbsent("auth-service.embedded.environment", "development");
        properties.putIfAbsent("auth-service.embedded.port", "8080");
        properties.putIfAbsent("auth-service.embedded.client-id", "");
        properties.putIfAbsent("auth-service.embedded.client-secret", "");
        properties.putIfAbsent("auth-service.embedded.tenant", "organizations");
        properties.putIfAbsent("auth-service.embedded.redirect-uri", "http://127.0.0.1:8080/callback");
        properties.putIfAbsent("auth-service.embedded.allowed-tenants", "");
        properties.putIfAbsent("auth-service.embedded.admin-bearer-token", "");
        properties.putIfAbsent("auth-service.embedded.participation-id-hash-secret", "");
        properties.putIfAbsent("auth-service.embedded.participation-ttl-minutes", "10");
        properties.putIfAbsent("auth-service.embedded.store-backend", "file");
        properties.putIfAbsent("auth-service.embedded.ticket-store", "geyser-edu-auth/participation-tickets.tsv");
        properties.putIfAbsent("auth-service.embedded.audit-log", "geyser-edu-auth/audit.tsv");
        properties.putIfAbsent("auth-service.embedded.verify-rate-limit-per-minute", "60");
        properties.putIfAbsent("auth-service.embedded.postgres-jdbc-url", "jdbc:postgresql://postgres:5432/geyser_edu");
        properties.putIfAbsent("auth-service.embedded.postgres-username", "geyser_edu");
        properties.putIfAbsent("auth-service.embedded.postgres-password", "");
        properties.putIfAbsent("auth-service.device-code.enabled", "true");
        properties.putIfAbsent("auth-service.device-code.start-url", "http://127.0.0.1:8080/api/device/start");
        properties.putIfAbsent("auth-service.device-code.poll-url", "http://127.0.0.1:8080/api/device/poll");
        properties.putIfAbsent("auth-service.bearer-token", "");
        properties.putIfAbsent("auth-service.timeout-millis", "5000");
    }

    private boolean bool(String key, boolean defaultValue) {
        return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(defaultValue)));
    }

    private int integer(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(defaultValue)));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private List<String> csv(String key, String defaultValue) {
        return Arrays.stream(properties.getProperty(key, defaultValue).split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    }

    private static void put(Map<String, String> values, String key, String value) {
        if (value != null && !value.isBlank()) {
            values.put(key, value);
        }
    }
}
