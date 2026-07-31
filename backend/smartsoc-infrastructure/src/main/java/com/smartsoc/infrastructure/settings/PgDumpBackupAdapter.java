package com.smartsoc.infrastructure.settings;

import com.smartsoc.application.settings.DatabaseBackupPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Sauvegarde réelle de PostgreSQL via l'outil client {@code pg_dump}
 * (format {@code custom}, restaurable par {@code pg_restore} — la
 * restauration elle-même n'est pas construite ici, action distincte et
 * plus sensible). Le mot de passe transite par la variable d'environnement
 * {@code PGPASSWORD}, jamais par un argument de ligne de commande (visible
 * via {@code ps}).
 */
@Component
public class PgDumpBackupAdapter implements DatabaseBackupPort {

    private static final DateTimeFormatter FILENAME_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final long TIMEOUT_SECONDS = 120;

    private final String host;
    private final String port;
    private final String database;
    private final String username;
    private final String password;
    private final String pgDumpCommand;

    public PgDumpBackupAdapter(
            @Value("${POSTGRES_HOST:localhost}") String host,
            @Value("${POSTGRES_PORT:5432}") String port,
            @Value("${POSTGRES_DB:smartsoc}") String database,
            @Value("${POSTGRES_USER:smartsoc}") String username,
            @Value("${POSTGRES_PASSWORD:}") String password,
            @Value("${smartsoc.backup.pg-dump-command:pg_dump}") String pgDumpCommand) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.pgDumpCommand = pgDumpCommand;
    }

    @Override
    public BackupResult exportDatabase() {
        ProcessBuilder processBuilder = new ProcessBuilder(pgDumpCommand,
                "--host=" + host, "--port=" + port, "--username=" + username,
                "--format=custom", "--no-password", database);
        processBuilder.environment().put("PGPASSWORD", password);

        try {
            Process process = processBuilder.start();
            byte[] content;
            String stderr;
            try (InputStream stdout = process.getInputStream()) {
                content = stdout.readAllBytes();
            }
            try (InputStream stderrStream = process.getErrorStream()) {
                stderr = new String(stderrStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BackupExecutionException(
                        "pg_dump n'a pas terminé dans le délai imparti (" + TIMEOUT_SECONDS + "s)", null);
            }
            if (process.exitValue() != 0) {
                throw new BackupExecutionException("pg_dump a échoué : " + stderr.trim(), null);
            }

            Instant now = Instant.now();
            String filename = "smartsoc-backup-%s.dump".formatted(FILENAME_TIMESTAMP.format(now));
            return new BackupResult(content, filename, now);
        } catch (IOException ex) {
            throw new BackupExecutionException("Impossible d'exécuter pg_dump : " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BackupExecutionException("Sauvegarde interrompue", ex);
        }
    }
}
