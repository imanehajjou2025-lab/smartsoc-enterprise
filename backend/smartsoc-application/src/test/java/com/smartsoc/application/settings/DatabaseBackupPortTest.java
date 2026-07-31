package com.smartsoc.application.settings;

import com.smartsoc.application.settings.DatabaseBackupPort.BackupResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseBackupPortTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-07-31T15:00:00Z");

    @Test
    void equalsComparesTheArrayContentNotItsReference() {
        BackupResult first = new BackupResult(new byte[] {1, 2, 3}, "a.dump", GENERATED_AT);
        BackupResult sameContent = new BackupResult(new byte[] {1, 2, 3}, "a.dump", GENERATED_AT);
        BackupResult differentContent = new BackupResult(new byte[] {9, 9, 9}, "a.dump", GENERATED_AT);

        assertThat(first).isEqualTo(sameContent);
        assertThat(first).isNotEqualTo(differentContent);
        assertThat(first).isNotEqualTo("not a BackupResult");
        assertThat(first).isEqualTo(first);
        assertThat(first.hashCode()).isEqualTo(sameContent.hashCode());
    }

    @Test
    void toStringReportsTheByteCountRatherThanDumpingTheArray() {
        BackupResult result = new BackupResult(new byte[] {1, 2, 3, 4}, "a.dump", GENERATED_AT);

        assertThat(result.toString()).contains("4 bytes").contains("a.dump");
    }
}
