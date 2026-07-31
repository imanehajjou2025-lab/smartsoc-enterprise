package com.smartsoc.infrastructure.settings;

import com.smartsoc.application.settings.DatabaseBackupPort.BackupExecutionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L'exécution réelle de {@code pg_dump} contre une vraie base est vérifiée
 * manuellement (aucun client pg_dump sur la machine de développement/CI) —
 * ce test couvre uniquement le chemin d'échec, déterministe et portable.
 */
class PgDumpBackupAdapterTest {

    @Test
    void exportDatabaseWrapsAMissingCommandIntoABackupExecutionException() {
        PgDumpBackupAdapter adapter = new PgDumpBackupAdapter(
                "localhost", "5432", "smartsoc", "smartsoc", "irrelevant",
                "pg_dump_command_does_not_exist_xyz");

        assertThatThrownBy(adapter::exportDatabase)
                .isInstanceOf(BackupExecutionException.class);
    }
}
