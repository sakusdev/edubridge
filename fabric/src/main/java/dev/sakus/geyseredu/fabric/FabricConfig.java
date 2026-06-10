package dev.sakus.geyseredu.fabric;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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

    public String authServiceVerifyUrl() {
        return properties.getProperty("auth-service.verify-url", "");
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
}
