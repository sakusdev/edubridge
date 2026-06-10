package dev.sakus.geyseredu.authservice;

import java.time.Instant;

public record DeviceAuthorization(
    String deviceCode,
    String userCode,
    String verificationUri,
    Instant expiresAt,
    int intervalSeconds,
    String message
) {
}
