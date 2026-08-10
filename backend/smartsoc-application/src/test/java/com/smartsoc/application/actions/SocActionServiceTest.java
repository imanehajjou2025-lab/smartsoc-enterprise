package com.smartsoc.application.actions;

import com.smartsoc.application.audit.ActorContext;
import com.smartsoc.application.audit.AuditRecorder;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.domain.assets.Asset;
import com.smartsoc.domain.assets.AssetCriticality;
import com.smartsoc.domain.assets.AssetExposure;
import com.smartsoc.domain.assets.AssetRepository;
import com.smartsoc.domain.assets.AssetType;
import com.smartsoc.domain.audit.AuditAction;
import com.smartsoc.domain.audit.AuditLogEntry;
import com.smartsoc.domain.audit.AuditLogQuery;
import com.smartsoc.domain.audit.AuditLogRepository;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.incidents.Incident;
import com.smartsoc.domain.incidents.IncidentRepository;
import com.smartsoc.domain.soar.ExecutionStatus;
import com.smartsoc.domain.soar.Playbook;
import com.smartsoc.domain.soar.PlaybookExecution;
import com.smartsoc.domain.soar.PlaybookExecutionRepository;
import com.smartsoc.domain.soar.PlaybookRepository;
import com.smartsoc.domain.soar.PlaybookStepTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocActionServiceTest {

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private AgentControlPort agentControlPort;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private PlaybookRepository playbookRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private PlaybookExecutionRepository playbookExecutionRepository;

    @Mock
    private WorkflowTriggerPort workflowTriggerPort;

    @Mock
    private WorkflowStatusPort workflowStatusPort;

    private SocActionService service;
    private final ActorContext actor = new ActorContext("analyst1", UUID.randomUUID(), "10.0.0.5");

    @BeforeEach
    void createService() {
        service = new SocActionService(assetRepository, agentControlPort, auditRecorder, auditLogRepository,
                playbookRepository, incidentRepository, playbookExecutionRepository, workflowTriggerPort,
                workflowStatusPort);
        lenient().when(auditLogRepository.search(any()))
                .thenReturn(new PageResult<>(java.util.List.of(), 0, 0, 1));
        lenient().when(playbookExecutionRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Playbook shuffleLinkedPlaybook() {
        return Playbook.declare(Playbook.DeclareCommand.builder()
                .name("Confinement ransomware")
                .steps(java.util.List.of(new PlaybookStepTemplate(0, "Isoler le poste", null)))
                .shuffleWorkflowId("fb0e09e3-402f-4d20-9bc1-f7fa845d4314")
                .shuffleWebhookPath("webhook_a0fa6c78-fa6c-41a1-ac56-3c7f514ba8f4")
                .build());
    }

    private static Incident anIncident() {
        return Incident.open("INC-2026-0099", "Multiple Windows Logon Failures", null, Severity.HIGH);
    }

    private static Asset wazuhManagedAsset() {
        Asset asset = Asset.register(Asset.RegistrationData.builder()
                .hostname("win10-client")
                .type(AssetType.OTHER)
                .criticality(AssetCriticality.MEDIUM)
                .exposure(AssetExposure.INTERNAL)
                .build());
        asset.applySyncMetadata("004", "wazuh", "Windows 10", Instant.now());
        return asset;
    }

    @Test
    void restartsTheAgentWhenHostnameIsConfirmedAndRecordsSuccess() {
        Asset asset = wazuhManagedAsset();
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));

        service.restartAgent(asset.getId(), "win10-client", "Agent bloque, redemarrage demande", actor);

        verify(agentControlPort).restart("004");
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditRecorder).record(eq(AuditAction.WAZUH_AGENT_RESTART_REQUESTED), eq("analyst1"),
                eq(actor.userId()), eq("ASSET"), eq(asset.getId().toString()), details.capture(), eq("10.0.0.5"));
        assertThat(details.getValue()).contains("outcome=SUCCESS");
    }

    @Test
    void hostnameConfirmationIsCaseAndSpaceInsensitiveButMustMatch() {
        Asset asset = wazuhManagedAsset();
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));

        service.restartAgent(asset.getId(), "  WIN10-CLIENT  ", "test", actor);

        verify(agentControlPort).restart("004");
    }

    @Test
    void rejectsWhenConfirmedHostnameDoesNotMatch() {
        Asset asset = wazuhManagedAsset();
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> service.restartAgent(asset.getId(), "wrong-host", "test", actor))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("does not match this asset");

        verify(agentControlPort, never()).restart(anyString());
        verify(auditRecorder, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsWhenReasonIsBlank() {
        Asset asset = wazuhManagedAsset();
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> service.restartAgent(asset.getId(), "win10-client", "   ", actor))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("reason");

        verify(agentControlPort, never()).restart(anyString());
    }

    @Test
    void rejectsWhenAssetIsNotWazuhManaged() {
        Asset manual = Asset.register(Asset.RegistrationData.builder()
                .hostname("srv-manual")
                .type(AssetType.SERVER)
                .criticality(AssetCriticality.HIGH)
                .exposure(AssetExposure.INTERNAL)
                .build());
        when(assetRepository.findById(manual.getId())).thenReturn(Optional.of(manual));

        assertThatThrownBy(() -> service.restartAgent(manual.getId(), "srv-manual", "test", actor))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("not managed by the Wazuh connector");

        verify(agentControlPort, never()).restart(anyString());
    }

    @Test
    void rejectsWhenAssetDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(assetRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restartAgent(missing, "whatever", "test", actor))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void recordsFailureAndRethrowsWhenTheConnectorFails() {
        Asset asset = wazuhManagedAsset();
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        doThrow(new SocConnectorException("Wazuh unavailable")).when(agentControlPort).restart("004");

        assertThatThrownBy(() -> service.restartAgent(asset.getId(), "win10-client", "test", actor))
                .isInstanceOf(SocConnectorException.class);

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditRecorder).record(eq(AuditAction.WAZUH_AGENT_RESTART_REQUESTED), any(), any(),
                any(), any(), details.capture(), any());
        assertThat(details.getValue()).contains("outcome=FAILURE").contains("Wazuh unavailable");
    }

    @Test
    void rejectsWhenTheHourlyCapOnThisAssetIsAlreadyReached() {
        Asset asset = wazuhManagedAsset();
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        // 3 tentatives deja enregistrees dans la derniere heure -- plafond atteint.
        AuditLogEntry priorAttempt = AuditLogEntry.record(AuditAction.WAZUH_AGENT_RESTART_REQUESTED,
                "someone", UUID.randomUUID(), "ASSET", asset.getId().toString(), "reason=x", "10.0.0.1");
        when(auditLogRepository.search(any(AuditLogQuery.class)))
                .thenReturn(new PageResult<>(java.util.List.of(priorAttempt, priorAttempt, priorAttempt), 3, 0, 1));

        assertThatThrownBy(() -> service.restartAgent(asset.getId(), "win10-client", "test", actor))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Too many attempts of this action on this target");

        verify(agentControlPort, never()).restart(anyString());
    }

    @Test
    void theRateCapQueryIsScopedToThisAssetAndThisActionOnly() {
        Asset asset = wazuhManagedAsset();
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));

        service.restartAgent(asset.getId(), "win10-client", "test", actor);

        ArgumentCaptor<AuditLogQuery> queryCaptor = ArgumentCaptor.forClass(AuditLogQuery.class);
        verify(auditLogRepository).search(queryCaptor.capture());
        assertThat(queryCaptor.getValue().action()).isEqualTo(AuditAction.WAZUH_AGENT_RESTART_REQUESTED);
        assertThat(queryCaptor.getValue().targetType()).isEqualTo("ASSET");
        assertThat(queryCaptor.getValue().targetId()).isEqualTo(asset.getId().toString());
    }

    // --- blockIp (active-response firewall-drop) ---

    @Test
    void blocksTheIpWhenHostnameIsConfirmedAndRecordsSuccessWithTheIpInDetails() {
        Asset asset = wazuhManagedAsset();
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));

        service.blockIp(asset.getId(), "win10-client", "203.0.113.42", "IP malveillante", actor);

        verify(agentControlPort).blockIp("004", "203.0.113.42");
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditRecorder).record(eq(AuditAction.WAZUH_AGENT_FIREWALL_DROP_REQUESTED), eq("analyst1"),
                eq(actor.userId()), eq("ASSET"), eq(asset.getId().toString()), details.capture(), eq("10.0.0.5"));
        assertThat(details.getValue()).contains("outcome=SUCCESS").contains("ip=203.0.113.42");
    }

    @Test
    void rejectsAnInvalidIpAddressFormat() {
        Asset asset = wazuhManagedAsset();
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));

        assertThatThrownBy(() -> service.blockIp(asset.getId(), "win10-client", "not-an-ip", "test", actor))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("IPv4");

        verify(agentControlPort, never()).blockIp(anyString(), anyString());
    }

    @Test
    void blockIpAndRestartRateCapsAreCountedSeparately() {
        Asset asset = wazuhManagedAsset();
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        // 3 redemarrages deja au plafond pour WAZUH_AGENT_RESTART_REQUESTED...
        when(auditLogRepository.search(argThat(q ->
                q != null && q.action() == AuditAction.WAZUH_AGENT_RESTART_REQUESTED)))
                .thenReturn(new PageResult<>(java.util.List.of(), 3, 0, 1));
        // ...mais aucune tentative de blocage IP enregistree : le quota est distinct.
        when(auditLogRepository.search(argThat(q ->
                q != null && q.action() == AuditAction.WAZUH_AGENT_FIREWALL_DROP_REQUESTED)))
                .thenReturn(new PageResult<>(java.util.List.of(), 0, 0, 1));

        assertThatThrownBy(() -> service.restartAgent(asset.getId(), "win10-client", "test", actor))
                .isInstanceOf(BusinessRuleViolationException.class);
        service.blockIp(asset.getId(), "win10-client", "203.0.113.42", "test", actor);

        verify(agentControlPort).blockIp("004", "203.0.113.42");
        verify(agentControlPort, never()).restart(anyString());
    }

    @Test
    void recordsFailureAndRethrowsWhenTheConnectorFailsToBlockTheIp() {
        Asset asset = wazuhManagedAsset();
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        doThrow(new SocConnectorException("Wazuh unavailable"))
                .when(agentControlPort).blockIp("004", "203.0.113.42");

        assertThatThrownBy(() -> service.blockIp(asset.getId(), "win10-client", "203.0.113.42", "test", actor))
                .isInstanceOf(SocConnectorException.class);

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditRecorder).record(eq(AuditAction.WAZUH_AGENT_FIREWALL_DROP_REQUESTED), any(), any(),
                any(), any(), details.capture(), any());
        assertThat(details.getValue()).contains("outcome=FAILURE").contains("ip=203.0.113.42");
    }

    // --- triggerShuffleWorkflow ---

    @Test
    void triggersTheWorkflowWhenPlaybookNameIsConfirmedAndRecordsSuccess() {
        Playbook playbook = shuffleLinkedPlaybook();
        Incident incident = anIncident();
        when(playbookRepository.findById(playbook.getId())).thenReturn(Optional.of(playbook));
        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));
        when(workflowTriggerPort.trigger(eq("webhook_a0fa6c78-fa6c-41a1-ac56-3c7f514ba8f4"), any()))
                .thenReturn("4855cf04-842b-4a86-9eef-05f2c796eef2");

        PlaybookExecution execution = service.triggerShuffleWorkflow(playbook.getId(), incident.getId(),
                "Confinement ransomware", "Alerte critique en cours", actor);

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
        assertThat(execution.getExternalExecutionId()).isEqualTo("4855cf04-842b-4a86-9eef-05f2c796eef2");
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditRecorder).record(eq(AuditAction.SHUFFLE_WORKFLOW_TRIGGER_REQUESTED), eq("analyst1"),
                eq(actor.userId()), eq("INCIDENT"), eq(incident.getId().toString()), details.capture(), eq("10.0.0.5"));
        assertThat(details.getValue()).contains("outcome=SUCCESS")
                .contains("executionId=4855cf04-842b-4a86-9eef-05f2c796eef2");
    }

    @Test
    void playbookNameConfirmationIsCaseInsensitiveButMustMatch() {
        Playbook playbook = shuffleLinkedPlaybook();
        Incident incident = anIncident();
        when(playbookRepository.findById(playbook.getId())).thenReturn(Optional.of(playbook));
        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> service.triggerShuffleWorkflow(playbook.getId(), incident.getId(),
                "un autre nom", "test", actor))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("does not match");

        verify(workflowTriggerPort, never()).trigger(anyString(), any());
    }

    @Test
    void rejectsAPlaybookNotLinkedToAShuffleWorkflow() {
        Playbook playbook = Playbook.declare(Playbook.DeclareCommand.builder()
                .name("Procedure documentaire seule")
                .steps(java.util.List.of(new PlaybookStepTemplate(0, "Consigner", null)))
                .build());
        Incident incident = anIncident();
        when(playbookRepository.findById(playbook.getId())).thenReturn(Optional.of(playbook));
        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> service.triggerShuffleWorkflow(playbook.getId(), incident.getId(),
                "Procedure documentaire seule", "test", actor))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("not linked to a Shuffle workflow");

        verify(workflowTriggerPort, never()).trigger(anyString(), any());
    }

    @Test
    void recordsStartFailedAndDoesNotThrowWhenShuffleRefusesTheTrigger() {
        Playbook playbook = shuffleLinkedPlaybook();
        Incident incident = anIncident();
        when(playbookRepository.findById(playbook.getId())).thenReturn(Optional.of(playbook));
        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));
        when(workflowTriggerPort.trigger(anyString(), any()))
                .thenThrow(new SocConnectorException("Shuffle unavailable"));

        PlaybookExecution execution = service.triggerShuffleWorkflow(playbook.getId(), incident.getId(),
                "Confinement ransomware", "test", actor);

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.START_FAILED);
        assertThat(execution.getExternalExecutionId()).isNull();
        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditRecorder).record(eq(AuditAction.SHUFFLE_WORKFLOW_TRIGGER_REQUESTED), any(), any(),
                any(), any(), details.capture(), any());
        assertThat(details.getValue()).contains("outcome=FAILURE").contains("Shuffle unavailable");
    }

    @Test
    void triggerRateCapIsScopedToTheIncidentAndThisActionOnly() {
        Playbook playbook = shuffleLinkedPlaybook();
        Incident incident = anIncident();
        when(playbookRepository.findById(playbook.getId())).thenReturn(Optional.of(playbook));
        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));
        when(workflowTriggerPort.trigger(anyString(), any())).thenReturn("exec-1");

        service.triggerShuffleWorkflow(playbook.getId(), incident.getId(), "Confinement ransomware", "test", actor);

        ArgumentCaptor<AuditLogQuery> queryCaptor = ArgumentCaptor.forClass(AuditLogQuery.class);
        verify(auditLogRepository).search(queryCaptor.capture());
        assertThat(queryCaptor.getValue().action()).isEqualTo(AuditAction.SHUFFLE_WORKFLOW_TRIGGER_REQUESTED);
        assertThat(queryCaptor.getValue().targetType()).isEqualTo("INCIDENT");
        assertThat(queryCaptor.getValue().targetId()).isEqualTo(incident.getId().toString());
    }

    // --- refreshShuffleWorkflowStatus (reconciliation en lecture) ---

    private static PlaybookExecution inProgressExternalExecution() {
        return PlaybookExecution.startExternal(UUID.randomUUID(), 1, "Confinement ransomware",
                UUID.randomUUID(), "4855cf04-842b-4a86-9eef-05f2c796eef2");
    }

    @Test
    void refreshCompletesTheExecutionWhenShuffleReportsSuccess() {
        PlaybookExecution execution = inProgressExternalExecution();
        Playbook playbook = shuffleLinkedPlaybook().toBuilder().id(execution.getPlaybookId()).build();
        when(playbookExecutionRepository.findById(execution.getId())).thenReturn(Optional.of(execution));
        when(playbookRepository.findById(execution.getPlaybookId())).thenReturn(Optional.of(playbook));
        when(workflowStatusPort.statusOf(playbook.getShuffleWorkflowId(), execution.getExternalExecutionId()))
                .thenReturn(new WorkflowExecutionStatus(WorkflowExecutionStatus.Outcome.SUCCEEDED, "FINISHED"));

        PlaybookExecution refreshed = service.refreshShuffleWorkflowStatus(execution.getId());

        assertThat(refreshed.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(refreshed.getResultSummary()).isEqualTo("FINISHED");
        verify(auditRecorder, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void refreshMarksTheExecutionOrphanedWhenShuffleNoLongerKnowsIt() {
        PlaybookExecution execution = inProgressExternalExecution();
        Playbook playbook = shuffleLinkedPlaybook().toBuilder().id(execution.getPlaybookId()).build();
        when(playbookExecutionRepository.findById(execution.getId())).thenReturn(Optional.of(execution));
        when(playbookRepository.findById(execution.getPlaybookId())).thenReturn(Optional.of(playbook));
        when(workflowStatusPort.statusOf(any(), any()))
                .thenReturn(new WorkflowExecutionStatus(WorkflowExecutionStatus.Outcome.NOT_FOUND, null));

        PlaybookExecution refreshed = service.refreshShuffleWorkflowStatus(execution.getId());

        assertThat(refreshed.getStatus()).isEqualTo(ExecutionStatus.ORPHANED);
    }

    @Test
    void refreshIsANoOpOnAnAlreadyTerminalExecution() {
        PlaybookExecution execution = inProgressExternalExecution();
        execution.completeExternally("deja termine");
        when(playbookExecutionRepository.findById(execution.getId())).thenReturn(Optional.of(execution));

        PlaybookExecution refreshed = service.refreshShuffleWorkflowStatus(execution.getId());

        assertThat(refreshed.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        verify(workflowStatusPort, never()).statusOf(any(), any());
        verify(playbookExecutionRepository, never()).save(any());
    }
}
