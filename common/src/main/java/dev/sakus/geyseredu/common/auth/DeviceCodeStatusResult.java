package dev.sakus.geyseredu.common.auth;

import java.time.Instant;
import java.util.Optional;

public record DeviceCodeStatusResult(
    String status,
    boolean valid,
    String tenantId,
    String subject,
    Optional<Instant> expiresAt,
    String message
) {
    public static DeviceCodeStatusResult pending(String message) {
        return new DeviceCodeStatusResult("pending", false, "", "", Optional.empty(), message);
    }

    public static DeviceCodeStatusResult failed(String message) {
        return new DeviceCodeStatusResult("failed", false, "", "", Optional.empty(), message);
    }
}
