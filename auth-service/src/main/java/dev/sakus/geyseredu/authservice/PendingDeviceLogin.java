package dev.sakus.geyseredu.authservice;

import java.time.Instant;

public record PendingDeviceLogin(
    String requestId,
    String deviceCode,
    String userCode,
    String verificationUri,
    Instant expiresAt,
    int intervalSeconds,
    String playerUuid,
    String playerName,
    String platform,
    Instant nextPollAt
) {
    public PendingDeviceLogin withNextPollAt(Instant value) {
        return new PendingDeviceLogin(
            requestId,
            deviceCode,
            userCode,
            verificationUri,
            expiresAt,
            intervalSeconds,
            playerUuid,
            playerName,
            platform,
            value
        );
    }
}
