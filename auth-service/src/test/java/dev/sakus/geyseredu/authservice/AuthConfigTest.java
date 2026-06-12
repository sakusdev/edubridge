package dev.sakus.geyseredu.authservice;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthConfigTest {
    @Test
    void acceptsValidProductionPostgresConfiguration() {
        AuthConfig config = config(
            "production",
            "https://auth.example.edu/callback",
            Set.of("tenant-a"),
            "postgres",
            "jdbc:postgresql://postgres:5432/geyser_edu",
            "geyser_edu",
            "postgres-password"
        );

        assertTrue(config.validationErrors().isEmpty());
        assertTrue(config.tenantAllowed("tenant-a"));
        assertFalse(config.tenantAllowed("tenant-b"));
    }

    @Test
    void rejectsUnsafeProductionConfiguration() {
        AuthConfig config = config(
            "production",
            "http://127.0.0.1:8080/callback",
            Set.of(),
            "file",
            "",
            "",
            ""
        );

        List<String> errors = config.validationErrors();
        assertTrue(errors.contains("GEYSER_EDU_ALLOWED_TENANTS must be explicit in production"));
        assertTrue(errors.contains("GEYSER_EDU_STORE_BACKEND must be postgres in production"));
        assertTrue(errors.contains("GEYSER_EDU_REDIRECT_URI must use https in production"));
    }

    @Test
    void rejectsInvalidTtlAndRateLimit() {
        AuthConfig config = new AuthConfig(
            "development",
            "client-id",
            "client-secret",
            "organizations",
            URI.create("http://127.0.0.1:8080/callback"),
            Set.of(),
            "verify-token",
            "",
            "hash-secret",
            Duration.ofMinutes(61),
            "file",
            Path.of("tickets.tsv"),
            "",
            "",
            "",
            Path.of("audit.tsv"),
            0,
            8080
        );

        List<String> errors = config.validationErrors();
        assertTrue(errors.contains("GEYSER_EDU_PARTICIPATION_TTL_MINUTES must be between 1 and 60"));
        assertTrue(errors.contains("GEYSER_EDU_VERIFY_RATE_LIMIT_PER_MINUTE must be greater than zero"));
    }

    @Test
    void allowsDeviceCodeConfigurationWithoutClientSecret() {
        AuthConfig config = new AuthConfig(
            "development",
            "client-id",
            "",
            "organizations",
            URI.create("http://127.0.0.1:8080/callback"),
            Set.of("tenant-a"),
            "verify-token",
            "",
            "hash-secret",
            Duration.ofMinutes(10),
            "file",
            Path.of("tickets.tsv"),
            "",
            "",
            "",
            Path.of("audit.tsv"),
            60,
            8080
        );

        assertTrue(config.validationErrors().isEmpty());
    }

    private static AuthConfig config(
        String environment,
        String redirectUri,
        Set<String> allowedTenants,
        String storeBackend,
        String postgresJdbcUrl,
        String postgresUsername,
        String postgresPassword
    ) {
        return new AuthConfig(
            environment,
            "client-id",
            "client-secret",
            "organizations",
            URI.create(redirectUri),
            allowedTenants,
            "verify-token",
            "admin-token",
            "0123456789abcdef0123456789abcdef",
            Duration.ofMinutes(10),
            storeBackend,
            Path.of("tickets.tsv"),
            postgresJdbcUrl,
            postgresUsername,
            postgresPassword,
            Path.of("audit.tsv"),
            60,
            8080
        );
    }
}
