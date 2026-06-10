package dev.sakus.geyseredu.common.auth;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RemoteDeviceCodeClient {
    private static final Pattern STRING_FIELD = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern INT_FIELD = Pattern.compile("\"%s\"\\s*:\\s*(\\d+)");
    private static final Pattern STARTED_FIELD = Pattern.compile("\"started\"\\s*:\\s*true");
    private static final Pattern VALID_FIELD = Pattern.compile("\"valid\"\\s*:\\s*true");

    private final HttpClient client;
    private final URI startUri;
    private final URI pollUri;
    private final String bearerToken;
    private final Duration timeout;

    public RemoteDeviceCodeClient(URI startUri, URI pollUri, String bearerToken, Duration timeout) {
        this.startUri = startUri;
        this.pollUri = pollUri;
        this.bearerToken = bearerToken;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .build();
    }

    public DeviceCodeStartResult start(String playerUuid, String playerName, String platform) throws IOException, InterruptedException {
        String body = "{"
            + "\"playerUuid\":\"" + escape(playerUuid) + "\","
            + "\"playerName\":\"" + escape(playerName) + "\","
            + "\"platform\":\"" + escape(platform) + "\""
            + "}";
        HttpResponse<String> response = send(startUri, body);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return DeviceCodeStartResult.failed("auth-service returned HTTP " + response.statusCode());
        }

        String responseBody = response.body();
        if (!STARTED_FIELD.matcher(responseBody).find()) {
            return DeviceCodeStartResult.failed(readString(responseBody, "message").orElse("device login was not started"));
        }
        return new DeviceCodeStartResult(
            true,
            readString(responseBody, "requestId").orElse(""),
            readString(responseBody, "userCode").orElse(""),
            readString(responseBody, "verificationUri").orElse(""),
            readString(responseBody, "expiresAt").flatMap(RemoteDeviceCodeClient::parseInstant).orElse(Instant.EPOCH),
            readInt(responseBody, "intervalSeconds").orElse(5),
            readString(responseBody, "message").orElse("")
        );
    }

    public DeviceCodeStatusResult poll(String requestId, String playerUuid) throws IOException, InterruptedException {
        String body = "{"
            + "\"requestId\":\"" + escape(requestId) + "\","
            + "\"playerUuid\":\"" + escape(playerUuid) + "\""
            + "}";
        HttpResponse<String> response = send(pollUri, body);
        if (response.statusCode() == 202) {
            return DeviceCodeStatusResult.pending(readString(response.body(), "message").orElse("authorization pending"));
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return DeviceCodeStatusResult.failed("auth-service returned HTTP " + response.statusCode());
        }

        String responseBody = response.body();
        String status = readString(responseBody, "status").orElse("unknown");
        boolean valid = VALID_FIELD.matcher(responseBody).find();
        if (!valid) {
            if ("pending".equals(status)) {
                return DeviceCodeStatusResult.pending(readString(responseBody, "message").orElse("authorization pending"));
            }
            return DeviceCodeStatusResult.failed(readString(responseBody, "message").orElse("device login failed"));
        }
        return new DeviceCodeStatusResult(
            status,
            true,
            readString(responseBody, "tenantId").orElse(""),
            readString(responseBody, "subject").orElse(""),
            readString(responseBody, "expiresAt").flatMap(RemoteDeviceCodeClient::parseInstant),
            readString(responseBody, "message").orElse("verified")
        );
    }

    private HttpResponse<String> send(URI uri, String body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (!bearerToken.isBlank()) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Optional<String> readString(String json, String field) {
        Matcher matcher = Pattern.compile(String.format(STRING_FIELD.pattern(), Pattern.quote(field))).matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1));
    }

    private static Optional<Integer> readInt(String json, String field) {
        Matcher matcher = Pattern.compile(String.format(INT_FIELD.pattern(), Pattern.quote(field))).matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(matcher.group(1)));
    }

    private static Optional<Instant> parseInstant(String value) {
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
