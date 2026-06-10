package dev.sakus.geyseredu.authservice;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceHttpTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private HttpServer server;

    @TempDir
    Path tempDir;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void readyEndpointReturnsSecurityHeaders() throws Exception {
        URI baseUri = startReadyService();

        HttpResponse<String> response = client.send(
            HttpRequest.newBuilder(baseUri.resolve("/health/ready")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"ready\":true"));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertEquals("no-referrer", response.headers().firstValue("Referrer-Policy").orElse(""));
    }

    @Test
    void verifyRequiresBearerToken() throws Exception {
        URI baseUri = startReadyService();

        HttpResponse<String> response = client.send(
            HttpRequest.newBuilder(baseUri.resolve("/api/participation/verify"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("unauthorized"));
    }

    @Test
    void verifyRejectsOversizedBody() throws Exception {
        URI baseUri = startReadyService();
        String oversizedBody = "{\"participationId\":\"" + "x".repeat(9000) + "\"}";

        HttpResponse<String> response = client.send(
            HttpRequest.newBuilder(baseUri.resolve("/api/participation/verify"))
                .header("Authorization", "Bearer verify-token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(oversizedBody))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(413, response.statusCode());
        assertTrue(response.body().contains("request body too large"));
    }

    @Test
    void adminListFallsBackToVerifyTokenInDevelopment() throws Exception {
        URI baseUri = startReadyService();

        HttpResponse<String> response = client.send(
            HttpRequest.newBuilder(baseUri.resolve("/api/admin/participation/list"))
                .header("Authorization", "Bearer verify-token")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());
        assertEquals("{\"tickets\":[]}", response.body());
    }

    private URI startReadyService() throws Exception {
        AuthConfig config = new AuthConfig(
            "development",
            "client-id",
            "client-secret",
            "organizations",
            URI.create("http://127.0.0.1/callback"),
            Set.of("tenant-a"),
            "verify-token",
            "",
            "0123456789abcdef0123456789abcdef",
            Duration.ofMinutes(10),
            "file",
            tempDir.resolve("tickets.tsv"),
            "",
            "",
            "",
            tempDir.resolve("audit.tsv"),
            60,
            0
        );
        server = new AuthServiceMain(config).startServer();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }
}
