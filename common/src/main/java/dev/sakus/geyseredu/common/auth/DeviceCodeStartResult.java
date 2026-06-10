package dev.sakus.geyseredu.common.auth;

import java.time.Instant;

public record DeviceCodeStartResult(
    boolean started,
    String requestId,
    String userCode,
    String verificationUri,
    Instant expiresAt,
    int intervalSeconds,
    String message
) {
    public static DeviceCodeStartResult failed(String message) {
        return new DeviceCodeStartResult(false, "", "", "", Instant.EPOCH, 5, message);
    }
}
