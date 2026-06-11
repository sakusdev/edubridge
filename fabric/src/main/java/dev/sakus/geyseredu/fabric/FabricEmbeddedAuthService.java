package dev.sakus.geyseredu.fabric;

import com.sun.net.httpserver.HttpServer;
import dev.sakus.geyseredu.authservice.AuthConfig;
import dev.sakus.geyseredu.authservice.AuthServiceMain;

public final class FabricEmbeddedAuthService {
    private HttpServer server;

    public void startIfEnabled(FabricConfig config) {
        if (!config.embeddedAuthServiceEnabled()) {
            return;
        }
        if (server != null) {
            return;
        }

        try {
            AuthConfig authConfig = AuthConfig.fromValues(config.embeddedAuthValues());
            server = new AuthServiceMain(authConfig).startServer();
            System.err.println("[GeyserEdu] Embedded auth-service listening on port " + authConfig.port() + ".");
        } catch (Exception ex) {
            System.err.println("[GeyserEdu] Failed to start embedded auth-service: " + ex.getMessage());
            ex.printStackTrace(System.err);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            System.err.println("[GeyserEdu] Embedded auth-service stopped.");
        }
    }
}
