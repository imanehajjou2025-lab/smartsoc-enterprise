package com.smartsoc.application.settings;

import com.smartsoc.application.audit.ActorContext;
import com.smartsoc.application.audit.AuditRecorder;
import com.smartsoc.application.settings.DatabaseBackupPort.BackupResult;
import com.smartsoc.domain.audit.AuditAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DatabaseBackupService {

    private final DatabaseBackupPort backupPort;
    private final AuditRecorder auditRecorder;

    public BackupResult exportDatabase(ActorContext actor) {
        BackupResult result = backupPort.exportDatabase();
        auditRecorder.record(AuditAction.BACKUP_EXPORTED, actor.username(), actor.userId(),
                null, null, "Fichier " + result.filename(), actor.ipAddress());
        return result;
    }
}
