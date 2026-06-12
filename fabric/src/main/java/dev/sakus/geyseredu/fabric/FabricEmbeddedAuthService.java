package dev.sakus.geyseredu.fabric;

import com.sun.net.httpserver.HttpServer;
import dev.sakus.geyseredu.authservice.AuthConfig;
import dev.sakus.geyseredu.authservice.AuthServiceMain;

import java.net.BindException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public final class FabricEmbeddedAuthService {
    private HttpServer server;
    private URI baseUri;

    public void startIfEnabled(FabricConfig config) {
        if (!config.embeddedAuthServiceEnabled()) {
            return;
        }
        if (server != null) {
            return;
        }

        try {
            Map<String, String> values = config.embeddedAuthValues();
            int configuredPort = Integer.parseInt(values.getOrDefault("GEYSER_EDU_AUTH_PORT", "8080"));
            startOnAvailablePort(values, configuredPort);
        } catch (Exception ex) {
            System.err.println("[GeyserEdu] Failed to start embedded auth-service: " + ex.getMessage());
            ex.printStackTrace(System.err);
        }
    }

    public URI deviceStartUri(String fallback) {
        if (baseUri != null) {
            return baseUri.resolve("/api/device/start");
        }
        return URI.create(fallback);
    }

    public URI devicePollUri(String fallback) {
        if (baseUri != null) {
            return baseUri.resolve("/api/device/poll");
        }
        return URI.create(fallback);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            baseUri = null;
            System.err.println("[GeyserEdu] Embedded auth-service stopped.");
        }
    }

    private void startOnAvailablePort(Map<String, String> baseValues, int configuredPort) throws Exception {
        Exception lastFailure = null;
        for (int port : candidatePorts(configuredPort)) {
            Map<String, String> values = new HashMap<>(baseValues);
            values.put("GEYSER_EDU_AUTH_PORT", Integer.toString(port));
            try {
                AuthConfig authConfig = AuthConfig.fromValues(values);
                server = new AuthServiceMain(authConfig).startServer();
                int actualPort = server.getAddress().getPort();
                baseUri = URI.create("http://127.0.0.1:" + actualPort + "/");
                System.err.println("[GeyserEdu] Embedded auth-service listening on " + baseUri + ".");
                if (actualPort != configuredPort) {
                    System.err.println("[GeyserEdu] Configured auth-service port " + configuredPort + " was unavailable; using " + actualPort + " for device-code requests.");
                }
                return;
            } catch (BindException ex) {
                lastFailure = ex;
                System.err.println("[GeyserEdu] Embedded auth-service port " + port + " is unavailable; trying another port.");
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
    }

    private static int[] candidatePorts(int configuredPort) {
        if (configuredPort <= 0) {
            return new int[]{0};
        }
        if (configuredPort == 8080) {
            return new int[]{8080, 18080, 18081, 0};
        }
        return new int[]{configuredPort, configuredPort + 1, 0};
    }
}
