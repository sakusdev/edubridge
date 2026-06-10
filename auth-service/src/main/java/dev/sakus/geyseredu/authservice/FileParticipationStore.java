package dev.sakus.geyseredu.authservice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class FileParticipationStore implements ParticipationStore {
    private final ParticipationIdGenerator idGenerator = new ParticipationIdGenerator();
    private final Map<String, ParticipationTicket> tickets = new ConcurrentHashMap<>();
    private final Path storePath;
    private final ParticipationIdHasher hasher;

    public FileParticipationStore(Path storePath, ParticipationIdHasher hasher) {
        this.storePath = storePath;
        this.hasher = hasher;
        load();
    }

    @Override
    public IssuedParticipation issue(String tenantId, String subject, Instant expiresAt) {
        prune();
        String id;
        String idHash;
        do {
            id = idGenerator.generate();
            idHash = hasher.hash(id);
        } while (tickets.containsKey(idHash));

        ParticipationTicket ticket = new ParticipationTicket(idHash, tenantId, subject, expiresAt);
        tickets.put(idHash, ticket);
        save();
        return new IssuedParticipation(id, ticket);
    }

    @Override
    public Optional<ParticipationTicket> consume(String id) {
        prune();
        String idHash = hasher.hash(id);
        ParticipationTicket ticket = tickets.remove(idHash);
        if (ticket == null || ticket.expired()) {
            save();
            return Optional.empty();
        }
        save();
        return Optional.of(ticket);
    }

    @Override
    public boolean revoke(String id) {
        prune();
        boolean removed = tickets.remove(hasher.hash(id)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    @Override
    public boolean revokeHash(String idHash) {
        prune();
        boolean removed = tickets.remove(idHash) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    @Override
    public List<ParticipationTicket> activeTickets() {
        prune();
        return tickets.values()
            .stream()
            .filter(ticket -> !ticket.expired())
            .sorted((left, right) -> left.expiresAt().compareTo(right.expiresAt()))
            .toList();
    }

    private void prune() {
        if (tickets.values().removeIf(ParticipationTicket::expired)) {
            save();
        }
    }

    private void load() {
        if (!Files.exists(storePath)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(storePath, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t", 4);
                if (parts.length != 4) {
                    continue;
                }
                ParticipationTicket ticket = new ParticipationTicket(
                    parts[0],
                    parts[1],
                    parts[2],
                    Instant.parse(parts[3])
                );
                if (!ticket.expired()) {
                    tickets.put(ticket.idHash(), ticket);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load participation ticket store " + storePath, ex);
        }
    }

    private synchronized void save() {
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = new ArrayList<>();
            lines.add("# idHash\ttenantId\tsubject\texpiresAt");
            tickets.values()
                .stream()
                .filter(ticket -> !ticket.expired())
                .sorted((left, right) -> left.expiresAt().compareTo(right.expiresAt()))
                .forEach(ticket -> lines.add(String.join(
                    "\t",
                    ticket.idHash(),
                    ticket.tenantId(),
                    ticket.subject(),
                    ticket.expiresAt().toString()
                )));
            Files.write(storePath, lines, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save participation ticket store " + storePath, ex);
        }
    }
}
