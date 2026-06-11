package dev.sakus.geyseredu.authservice;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record AuthConfig(
    String environment,
    String clientId,
    String clientSecret,
    String tenant,
    URI redirectUri,
    Set<String> allowedTenants,
    String verifyBearerToken,
    String adminBearerToken,
    String participationIdHashSecret,
    Duration participationTtl,
    String storeBackend,
    Path ticketStorePath,
    String postgresJdbcUrl,
    String postgresUsername,
    String postgresPassword,
    Path auditLogPath,
    int verifyRateLimitPerMinute,
    int port
) {
    public static AuthConfig fromEnv() {
        return fromValues(Map.of());
    }

    public static AuthConfig fromValues(Map<String, String> values) {
        return new AuthConfig(
            value(values, "GEYSER_EDU_ENV", "development"),
            value(values, "GEYSER_EDU_CLIENT_ID", ""),
            value(values, "GEYSER_EDU_CLIENT_SECRET", ""),
            value(values, "GEYSER_EDU_TENANT", "organizations"),
            URI.create(value(values, "GEYSER_EDU_REDIRECT_URI", "http://127.0.0.1:8080/callback")),
            csv(value(values, "GEYSER_EDU_ALLOWED_TENANTS", "")),
            value(values, "GEYSER_EDU_VERIFY_BEARER_TOKEN", ""),
            value(values, "GEYSER_EDU_ADMIN_BEARER_TOKEN", ""),
            value(values, "GEYSER_EDU_PARTICIPATION_ID_HASH_SECRET", ""),
            Duration.ofMinutes(Long.parseLong(value(values, "GEYSER_EDU_PARTICIPATION_TTL_MINUTES", "10"))),
            value(values, "GEYSER_EDU_STORE_BACKEND", "file"),
            Path.of(value(values, "GEYSER_EDU_TICKET_STORE", "data/participation-tickets.tsv")),
            value(values, "GEYSER_EDU_POSTGRES_JDBC_URL", "jdbc:postgresql://postgres:5432/geyser_edu"),
            value(values, "GEYSER_EDU_POSTGRES_USERNAME", "geyser_edu"),
            value(values, "GEYSER_EDU_POSTGRES_PASSWORD", ""),
            Path.of(value(values, "GEYSER_EDU_AUDIT_LOG", "logs/audit.tsv")),
            Integer.parseInt(value(values, "GEYSER_EDU_VERIFY_RATE_LIMIT_PER_MINUTE", "60")),
            Integer.parseInt(value(values, "GEYSER_EDU_AUTH_PORT", "8080"))
        );
    }

    public boolean tenantAllowed(String tenantId) {
        return allowedTenants.isEmpty() || allowedTenants.contains(tenantId);
    }

    public boolean production() {
        return "production".equalsIgnoreCase(environment);
    }

    public List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        if (clientId.isBlank()) {
            errors.add("GEYSER_EDU_CLIENT_ID is required");
        }
        if (clientSecret.isBlank()) {
            errors.add("GEYSER_EDU_CLIENT_SECRET is required");
        }
        if (tenant.isBlank()) {
            errors.add("GEYSER_EDU_TENANT is required");
        }
        if (verifyBearerToken.isBlank()) {
            errors.add("GEYSER_EDU_VERIFY_BEARER_TOKEN is required");
        }
        if (production() && adminBearerToken.isBlank()) {
            errors.add("GEYSER_EDU_ADMIN_BEARER_TOKEN is required in production");
        }
        if (participationIdHashSecret.isBlank()) {
            errors.add("GEYSER_EDU_PARTICIPATION_ID_HASH_SECRET is required");
        }
        if (production() && participationIdHashSecret.length() < 32) {
            errors.add("GEYSER_EDU_PARTICIPATION_ID_HASH_SECRET must be at least 32 characters in production");
        }
        if (production() && allowedTenants.isEmpty()) {
            errors.add("GEYSER_EDU_ALLOWED_TENANTS must be explicit in production");
        }
        if (!"file".equalsIgnoreCase(storeBackend) && !"postgres".equalsIgnoreCase(storeBackend)) {
            errors.add("GEYSER_EDU_STORE_BACKEND must be file or postgres");
        }
        if (production() && !"postgres".equalsIgnoreCase(storeBackend)) {
            errors.add("GEYSER_EDU_STORE_BACKEND must be postgres in production");
        }
        if ("postgres".equalsIgnoreCase(storeBackend)) {
            if (postgresJdbcUrl.isBlank()) {
                errors.add("GEYSER_EDU_POSTGRES_JDBC_URL is required when using postgres");
            }
            if (postgresUsername.isBlank()) {
                errors.add("GEYSER_EDU_POSTGRES_USERNAME is required when using postgres");
            }
            if (postgresPassword.isBlank()) {
                errors.add("GEYSER_EDU_POSTGRES_PASSWORD is required when using postgres");
            }
        }
        if (production() && !"https".equalsIgnoreCase(redirectUri.getScheme())) {
            errors.add("GEYSER_EDU_REDIRECT_URI must use https in production");
        }
        if (participationTtl.isNegative() || participationTtl.isZero() || participationTtl.toMinutes() > 60) {
            errors.add("GEYSER_EDU_PARTICIPATION_TTL_MINUTES must be between 1 and 60");
        }
        if (verifyRateLimitPerMinute <= 0) {
            errors.add("GEYSER_EDU_VERIFY_RATE_LIMIT_PER_MINUTE must be greater than zero");
        }
        return errors;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String value(Map<String, String> values, String key, String fallback) {
        String configured = values.get(key);
        return configured == null || configured.isBlank() ? env(key, fallback) : configured;
    }

    private static Set<String> csv(String value) {
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(entry -> !entry.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }
}
