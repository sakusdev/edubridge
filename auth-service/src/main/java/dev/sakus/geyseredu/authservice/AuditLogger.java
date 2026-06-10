package dev.sakus.geyseredu.authservice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public final class AuditLogger {
    private final Path path;

    public AuditLogger(Path path) {
        this.path = path;
    }

    public synchronized void log(String event, String subject, String tenantId, String detail) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String line = String.join(
                "\t",
                Instant.now().toString(),
                sanitize(event),
                sanitize(subject),
                sanitize(tenantId),
                sanitize(detail)
            ) + System.lineSeparator();
            Files.writeString(
                path,
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException ex) {
            System.err.println("Failed to write audit log: " + ex.getMessage());
        }
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
