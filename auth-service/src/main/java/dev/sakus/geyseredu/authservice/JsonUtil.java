package dev.sakus.geyseredu.authservice;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonUtil {
    private JsonUtil() {
    }

    public static Optional<String> stringField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(unescape(matcher.group(1)));
    }

    public static Optional<Long> longField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(matcher.group(1)));
    }

    public static String idTokenPayload(String idToken) {
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("ID token does not contain a payload.");
        }
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        return new String(payload, StandardCharsets.UTF_8);
    }

    public static String jwtHeader(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("JWT does not contain a header.");
        }
        byte[] header = Base64.getUrlDecoder().decode(parts[0]);
        return new String(header, StandardCharsets.UTF_8);
    }

    public static String jsonString(String value) {
        return "\"" + escape(value) + "\"";
    }

    public static String escape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    private static String unescape(String value) {
        return value
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\\\", "\\");
    }
}
