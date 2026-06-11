package dev.sakus.geyseredu.auth;

import com.sun.net.httpserver.HttpServer;
import dev.sakus.geyseredu.authservice.AuthConfig;
import dev.sakus.geyseredu.authservice.AuthServiceMain;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public final class EmbeddedAuthService {
    private final JavaPlugin plugin;
    private HttpServer server;

    public EmbeddedAuthService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void startIfEnabled() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("auth-service.embedded.enabled", false)) {
            return;
        }
        if (server != null) {
            return;
        }

        try {
            AuthConfig authConfig = AuthConfig.fromValues(values(config, plugin.getDataFolder().toPath()));
            server = new AuthServiceMain(authConfig).startServer();
            plugin.getLogger().info("Embedded auth-service listening on port " + authConfig.port() + ".");
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start embedded auth-service.", ex);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            plugin.getLogger().info("Embedded auth-service stopped.");
        }
    }

    private static Map<String, String> values(FileConfiguration config, Path dataDir) {
        Map<String, String> values = new HashMap<>();
        put(values, "GEYSER_EDU_ENV", config.getString("auth-service.embedded.environment", "development"));
        put(values, "GEYSER_EDU_CLIENT_ID", config.getString("auth-service.embedded.client-id", ""));
        put(values, "GEYSER_EDU_CLIENT_SECRET", config.getString("auth-service.embedded.client-secret", ""));
        put(values, "GEYSER_EDU_TENANT", config.getString("auth-service.embedded.tenant", "organizations"));
        put(values, "GEYSER_EDU_REDIRECT_URI", config.getString("auth-service.embedded.redirect-uri", "http://127.0.0.1:8080/callback"));
        put(values, "GEYSER_EDU_ALLOWED_TENANTS", String.join(",", config.getStringList("auth-service.embedded.allowed-tenants")));
        put(values, "GEYSER_EDU_VERIFY_BEARER_TOKEN", config.getString("auth-service.bearer-token", ""));
        put(values, "GEYSER_EDU_ADMIN_BEARER_TOKEN", config.getString("auth-service.embedded.admin-bearer-token", ""));
        put(values, "GEYSER_EDU_PARTICIPATION_ID_HASH_SECRET", config.getString("auth-service.embedded.participation-id-hash-secret", ""));
        put(values, "GEYSER_EDU_PARTICIPATION_TTL_MINUTES", Integer.toString(config.getInt("auth-service.embedded.participation-ttl-minutes", 10)));
        put(values, "GEYSER_EDU_STORE_BACKEND", config.getString("auth-service.embedded.store-backend", "file"));
        put(values, "GEYSER_EDU_TICKET_STORE", dataDir.resolve(config.getString("auth-service.embedded.ticket-store", "auth-service/participation-tickets.tsv")).toString());
        put(values, "GEYSER_EDU_AUDIT_LOG", dataDir.resolve(config.getString("auth-service.embedded.audit-log", "auth-service/audit.tsv")).toString());
        put(values, "GEYSER_EDU_VERIFY_RATE_LIMIT_PER_MINUTE", Integer.toString(config.getInt("auth-service.embedded.verify-rate-limit-per-minute", 60)));
        put(values, "GEYSER_EDU_AUTH_PORT", Integer.toString(config.getInt("auth-service.embedded.port", 8080)));
        put(values, "GEYSER_EDU_POSTGRES_JDBC_URL", config.getString("auth-service.embedded.postgres-jdbc-url", "jdbc:postgresql://postgres:5432/geyser_edu"));
        put(values, "GEYSER_EDU_POSTGRES_USERNAME", config.getString("auth-service.embedded.postgres-username", "geyser_edu"));
        put(values, "GEYSER_EDU_POSTGRES_PASSWORD", config.getString("auth-service.embedded.postgres-password", ""));
        return values;
    }

    private static void put(Map<String, String> values, String key, String value) {
        if (value != null && !value.isBlank()) {
            values.put(key, value);
        }
    }
}
