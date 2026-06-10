package dev.sakus.geyseredu.authservice;

import java.security.SecureRandom;
import java.util.Base64;

public final class ParticipationIdGenerator {
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
