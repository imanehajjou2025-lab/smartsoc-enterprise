package com.smartsoc.application.audit;

import com.smartsoc.domain.audit.AuditAction;
import com.smartsoc.domain.audit.AuditLogEntry;
import com.smartsoc.domain.audit.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditRecorderTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Test
    void recordSavesAnEntryBuiltFromTheGivenFields() {
        AuditRecorder recorder = new AuditRecorder(auditLogRepository);
        UUID actorId = UUID.randomUUID();

        recorder.record(AuditAction.USER_DELETED, "admin", actorId, "User", "target-1",
                "compte desactive", "203.0.113.7");

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLogEntry saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo(AuditAction.USER_DELETED);
        assertThat(saved.getActorUsername()).isEqualTo("admin");
        assertThat(saved.getActorId()).isEqualTo(actorId);
        assertThat(saved.getTargetId()).isEqualTo("target-1");
    }

    @Test
    void recordNeverThrowsEvenWhenPersistenceFails() {
        AuditRecorder recorder = new AuditRecorder(auditLogRepository);
        when(auditLogRepository.save(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> recorder.record(AuditAction.LOGIN_FAILED, "admin", null,
                null, null, null, null))
                .doesNotThrowAnyException();
    }
}
