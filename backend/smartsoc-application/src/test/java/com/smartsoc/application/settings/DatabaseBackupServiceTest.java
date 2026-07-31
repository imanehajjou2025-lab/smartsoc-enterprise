package com.smartsoc.application.settings;

import com.smartsoc.application.audit.ActorContext;
import com.smartsoc.application.audit.AuditRecorder;
import com.smartsoc.application.settings.DatabaseBackupPort.BackupResult;
import com.smartsoc.domain.audit.AuditAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseBackupServiceTest {

    @Mock
    private DatabaseBackupPort backupPort;

    @Mock
    private AuditRecorder auditRecorder;

    @Test
    void exportDatabaseDelegatesToThePortAndRecordsAnAuditEntry() {
        DatabaseBackupService service = new DatabaseBackupService(backupPort, auditRecorder);
        BackupResult result = new BackupResult(new byte[] {1, 2, 3}, "smartsoc-backup-test.dump", Instant.now());
        when(backupPort.exportDatabase()).thenReturn(result);
        ActorContext actor = new ActorContext("admin", UUID.randomUUID(), "203.0.113.7");

        BackupResult returned = service.exportDatabase(actor);

        assertThat(returned).isEqualTo(result);
        verify(auditRecorder).record(eq(AuditAction.BACKUP_EXPORTED), eq("admin"), eq(actor.userId()),
                any(), any(), any(), eq("203.0.113.7"));
    }
}
