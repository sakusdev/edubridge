package dev.sakus.geyseredu.authservice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceMainTest {
    @Test
    void matchesBearerTokenHeader() {
        assertTrue(AuthServiceMain.bearerTokenMatches("server-token", "Bearer server-token"));
    }

    @Test
    void rejectsMissingOrDifferentBearerTokenHeader() {
        assertFalse(AuthServiceMain.bearerTokenMatches("server-token", null));
        assertFalse(AuthServiceMain.bearerTokenMatches("server-token", ""));
        assertFalse(AuthServiceMain.bearerTokenMatches("server-token", "server-token"));
        assertFalse(AuthServiceMain.bearerTokenMatches("server-token", "Bearer other-token"));
        assertFalse(AuthServiceMain.bearerTokenMatches("", "Bearer server-token"));
    }
}
