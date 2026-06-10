package dev.sakus.geyseredu.authservice;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PostgresParticipationStore implements ParticipationStore {
    private final ParticipationIdGenerator idGenerator = new ParticipationIdGenerator();
    private final ParticipationIdHasher hasher;
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public PostgresParticipationStore(
        String jdbcUrl,
        String username,
        String password,
        ParticipationIdHasher hasher
    ) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.hasher = hasher;
        initialize();
    }

    @Override
    public IssuedParticipation issue(String tenantId, String subject, Instant expiresAt) {
        pruneExpired();
        for (int attempt = 0; attempt < 5; attempt++) {
            String id = idGenerator.generate();
            String idHash = hasher.hash(id);
            ParticipationTicket ticket = new ParticipationTicket(idHash, tenantId, subject, expiresAt);
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement("""
                     insert into participation_tickets (id_hash, tenant_id, subject, expires_at)
                     values (?, ?, ?, ?)
                     on conflict do nothing
                     """)) {
                statement.setString(1, idHash);
                statement.setString(2, tenantId);
                statement.setString(3, subject);
                statement.setTimestamp(4, Timestamp.from(expiresAt));
                if (statement.executeUpdate() == 1) {
                    return new IssuedParticipation(id, ticket);
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to issue participation ticket", ex);
            }
        }
        throw new IllegalStateException("Failed to allocate a unique participation ID after retries.");
    }

    @Override
    public Optional<ParticipationTicket> consume(String participationId) {
        pruneExpired();
        String idHash = hasher.hash(participationId);
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement("""
                select id_hash, tenant_id, subject, expires_at
                from participation_tickets
                where id_hash = ? and expires_at > now()
                for update
                """)) {
                select.setString(1, idHash);
                try (ResultSet result = select.executeQuery()) {
                    if (!result.next()) {
                        connection.commit();
                        return Optional.empty();
                    }
                    ParticipationTicket ticket = readTicket(result);
                    try (PreparedStatement delete = connection.prepareStatement(
                        "delete from participation_tickets where id_hash = ?"
                    )) {
                        delete.setString(1, idHash);
                        delete.executeUpdate();
                    }
                    connection.commit();
                    return Optional.of(ticket);
                }
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to consume participation ticket", ex);
        }
    }

    @Override
    public boolean revoke(String participationId) {
        return revokeHash(hasher.hash(participationId));
    }

    @Override
    public boolean revokeHash(String idHash) {
        pruneExpired();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "delete from participation_tickets where id_hash = ?"
             )) {
            statement.setString(1, idHash);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to revoke participation ticket", ex);
        }
    }

    @Override
    public List<ParticipationTicket> activeTickets() {
        pruneExpired();
        List<ParticipationTicket> tickets = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                 select id_hash, tenant_id, subject, expires_at
                 from participation_tickets
                 where expires_at > now()
                 order by expires_at asc
                 """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                tickets.add(readTicket(result));
            }
            return tickets;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list participation tickets", ex);
        }
    }

    private void initialize() {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                 create table if not exists participation_tickets (
                     id_hash text primary key,
                     tenant_id text not null,
                     subject text not null,
                     expires_at timestamptz not null,
                     created_at timestamptz not null default now()
                 )
                 """)) {
            statement.executeUpdate();
            try (PreparedStatement index = connection.prepareStatement(
                "create index if not exists participation_tickets_expires_at_idx on participation_tickets (expires_at)"
            )) {
                index.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to initialize PostgreSQL participation store", ex);
        }
    }

    private void pruneExpired() {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                 "delete from participation_tickets where expires_at <= now()"
             )) {
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to prune participation tickets", ex);
        }
    }

    private ParticipationTicket readTicket(ResultSet result) throws SQLException {
        return new ParticipationTicket(
            result.getString("id_hash"),
            result.getString("tenant_id"),
            result.getString("subject"),
            result.getTimestamp("expires_at").toInstant()
        );
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }
}
