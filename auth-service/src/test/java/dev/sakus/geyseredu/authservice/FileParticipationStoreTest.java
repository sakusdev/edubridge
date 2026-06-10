package dev.sakus.geyseredu.authservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileParticipationStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void consumesParticipationIdOnlyOnceAndStoresOnlyHash() throws Exception {
        Path storePath = tempDir.resolve("tickets.tsv");
        ParticipationIdHasher hasher = new ParticipationIdHasher("test-secret");
        FileParticipationStore store = new FileParticipationStore(storePath, hasher);

        ParticipationStore.IssuedParticipation issued = store.issue(
            "tenant-a",
            "user-a",
            Instant.now().plus(10, ChronoUnit.MINUTES)
        );

        String fileContent = Files.readString(storePath);
        assertFalse(fileContent.contains(issued.plainId()));
        assertTrue(fileContent.contains(hasher.hash(issued.plainId())));

        var consumed = store.consume(issued.plainId());
        assertTrue(consumed.isPresent());
        assertEquals("tenant-a", consumed.get().tenantId());
        assertEquals("user-a", consumed.get().subject());
        assertTrue(store.consume(issued.plainId()).isEmpty());
        assertTrue(store.activeTickets().isEmpty());
    }

    @Test
    void reloadsActiveTicketsFromDiskAndRevokesByHash() {
        Path storePath = tempDir.resolve("tickets.tsv");
        ParticipationIdHasher hasher = new ParticipationIdHasher("test-secret");
        FileParticipationStore firstStore = new FileParticipationStore(storePath, hasher);
        ParticipationStore.IssuedParticipation issued = firstStore.issue(
            "tenant-a",
            "user-a",
            Instant.now().plus(10, ChronoUnit.MINUTES)
        );

        FileParticipationStore secondStore = new FileParticipationStore(storePath, hasher);
        assertEquals(1, secondStore.activeTickets().size());
        assertTrue(secondStore.revokeHash(issued.ticket().idHash()));
        assertTrue(secondStore.consume(issued.plainId()).isEmpty());
    }

    @Test
    void doesNotConsumeExpiredTickets() {
        FileParticipationStore store = new FileParticipationStore(
            tempDir.resolve("tickets.tsv"),
            new ParticipationIdHasher("test-secret")
        );

        ParticipationStore.IssuedParticipation issued = store.issue(
            "tenant-a",
            "user-a",
            Instant.now().minus(1, ChronoUnit.MINUTES)
        );

        assertTrue(store.consume(issued.plainId()).isEmpty());
        assertTrue(store.activeTickets().isEmpty());
    }
}
