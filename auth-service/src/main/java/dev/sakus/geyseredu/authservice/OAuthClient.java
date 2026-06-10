package dev.sakus.geyseredu.authservice;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

public final class OAuthClient {
    private final AuthConfig config;
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public OAuthClient(AuthConfig config) {
        this.config = config;
    }

    public URI authorizeUri(String state) {
        String query = form(Map.of(
            "client_id", config.clientId(),
            "response_type", "code",
            "redirect_uri", config.redirectUri().toString(),
            "response_mode", "query",
            "scope", "openid profile email",
            "state", state,
            "prompt", "select_account"
        ));
        return URI.create("https://login.microsoftonline.com/" + enc(config.tenant()) + "/oauth2/v2.0/authorize?" + query);
    }

    public String exchangeCodeForIdToken(String code) throws IOException, InterruptedException {
        String body = form(Map.of(
            "client_id", config.clientId(),
            "client_secret", config.clientSecret(),
            "grant_type", "authorization_code",
            "code", code,
            "redirect_uri", config.redirectUri().toString(),
            "scope", "openid profile email"
        ));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://login.microsoftonline.com/" + enc(config.tenant()) + "/oauth2/v2.0/token"))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Microsoft token endpoint returned HTTP " + response.statusCode() + ": " + response.body());
        }
        return JsonUtil.stringField(response.body(), "id_token")
            .orElseThrow(() -> new IOException("Token response did not include id_token."));
    }

    private static String form(Map<String, String> values) {
        return values.entrySet()
            .stream()
            .map(entry -> enc(entry.getKey()) + "=" + enc(entry.getValue()))
            .collect(Collectors.joining("&"));
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
