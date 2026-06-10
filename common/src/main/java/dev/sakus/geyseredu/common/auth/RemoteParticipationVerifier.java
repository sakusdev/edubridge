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

public final class RemoteParticipationVerifier implements ParticipationVerifier {
    private static final Pattern STRING_FIELD = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern VALID_FIELD = Pattern.compile("\"valid\"\\s*:\\s*true");

    private final HttpClient client;
    private final URI verifyUri;
    private final String bearerToken;
    private final Duration timeout;

    public RemoteParticipationVerifier(URI verifyUri, String bearerToken, Duration timeout) {
        this.verifyUri = verifyUri;
        this.bearerToken = bearerToken;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .build();
    }

    @Override
    public ParticipationVerificationResult verify(ParticipationVerificationRequest request) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(verifyUri)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(toJson(request)));

        if (!bearerToken.isBlank()) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return ParticipationVerificationResult.invalid("participation ID was not found");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return ParticipationVerificationResult.invalid("auth-service returned HTTP " + response.statusCode());
        }

        String body = response.body();
        boolean valid = VALID_FIELD.matcher(body).find();
        if (!valid) {
            return ParticipationVerificationResult.invalid(readString(body, "message").orElse("participation ID was rejected"));
        }

        String tenantId = readString(body, "tenantId").orElse("");
        String subject = readString(body, "subject").orElse("");
        Optional<Instant> expiresAt = readString(body, "expiresAt").flatMap(RemoteParticipationVerifier::parseInstant);
        String message = readString(body, "message").orElse("verified");
        return new ParticipationVerificationResult(true, tenantId, subject, expiresAt, message);
    }

    private static String toJson(ParticipationVerificationRequest request) {
        return "{"
            + "\"participationId\":\"" + escape(request.participationId()) + "\","
            + "\"playerUuid\":\"" + request.playerUuid() + "\","
            + "\"playerName\":\"" + escape(request.playerName()) + "\","
            + "\"platform\":\"" + escape(request.platform()) + "\""
            + "}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Optional<String> readString(String json, String field) {
        Matcher matcher = Pattern.compile(String.format(STRING_FIELD.pattern(), Pattern.quote(field))).matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1));
    }

    private static Optional<Instant> parseInstant(String value) {
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }
}
