package dev.sakus.geyseredu.common.auth;

import java.io.IOException;

public interface ParticipationVerifier {
    ParticipationVerificationResult verify(ParticipationVerificationRequest request) throws IOException, InterruptedException;
}
