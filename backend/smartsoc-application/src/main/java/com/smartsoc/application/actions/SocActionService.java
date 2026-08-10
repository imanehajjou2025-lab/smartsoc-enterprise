package com.smartsoc.application.actions;

import com.smartsoc.application.audit.ActorContext;
import com.smartsoc.application.audit.AuditRecorder;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.assets.Asset;
import com.smartsoc.domain.assets.AssetRepository;
import com.smartsoc.domain.audit.AuditAction;
import com.smartsoc.domain.audit.AuditLogQuery;
import com.smartsoc.domain.audit.AuditLogRepository;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.common.TextNormalization;
import com.smartsoc.domain.incidents.Incident;
import com.smartsoc.domain.incidents.IncidentRepository;
import com.smartsoc.domain.soar.ExecutionStatus;
import com.smartsoc.domain.soar.Playbook;
import com.smartsoc.domain.soar.PlaybookExecution;
import com.smartsoc.domain.soar.PlaybookExecutionRepository;
import com.smartsoc.domain.soar.PlaybookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * POINT DE PASSAGE UNIQUE des actions à effet réel sur le monde
 * extérieur (ADR-014 phase 5, sous-contexte {@code actions} — ISOLÉ,
 * jamais confondu avec les connecteurs en lecture). Garde-fous
 * non négociables, tous appliqués ICI, jamais laissés à l'appelant :
 *
 * <ul>
 *   <li><b>Cible nommée explicitement.</b> {@code confirmHostname} doit
 *       correspondre EXACTEMENT au hostname connu de l'actif — un clic
 *       sur la mauvaise ligne échoue plutôt que d'agir sur la mauvaise
 *       machine.</li>
 *   <li><b>Motif obligatoire.</b> Jamais implicite, même doctrine que la
 *       révocation d'un IOC.</li>
 *   <li><b>Plafond horaire.</b> Au-delà de {@link #MAX_ACTIONS_PER_HOUR}
 *       exécutions sur LE MÊME actif dans l'heure, rejeté — protège
 *       contre un double-clic, une boucle, un script qui s'emballe.</li>
 *   <li><b>Aucun retry.</b> Ni ici, ni dans l'adaptateur infrastructure :
 *       rejouer une action, c'est agir une seconde fois sur le monde
 *       réel, jamais une simple relecture.</li>
 *   <li><b>Audit nominatif systématique.</b> Un échec est audité au même
 *       titre qu'un succès (voir {@link AuditAction#WAZUH_AGENT_RESTART_REQUESTED}) :
 *       le journal doit refléter chaque TENTATIVE, pas seulement les
 *       réussites.</li>
 * </ul>
 *
 * <p>Le RBAC (ANALYST+) est appliqué en amont, côté contrôleur — cette
 * classe suppose un appelant déjà autorisé, mais ne fait confiance à
 * rien d'autre venu de l'appelant.
 */
@Service
@RequiredArgsConstructor
public class SocActionService {

    private static final String TARGET_TYPE_ASSET = "ASSET";
    private static final String TARGET_TYPE_INCIDENT = "INCIDENT";
    private static final int MAX_ACTIONS_PER_HOUR = 3;
    private static final Duration RATE_WINDOW = Duration.ofHours(1);

    /** ADR-014 phase 5 : projection simple, jamais fait passer pour la sévérité Wazuh d'origine. */
    private static final Map<Severity, Integer> SEVERITY_TO_SHUFFLE = Map.of(
            Severity.CRITICAL, 4, Severity.HIGH, 3, Severity.MEDIUM, 2, Severity.LOW, 1, Severity.INFO, 0);

    /** Même expression que {@code IndicatorType.IPV4} (domaine CTI) — dupliquée
     * plutôt que référencée : {@code actions} ne dépend jamais de {@code intelligence},
     * bounded contexts distincts, même si le motif de validation coïncide. */
    private static final Pattern IPV4_FORMAT = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    private final AssetRepository assetRepository;
    private final AgentControlPort agentControlPort;
    private final AuditRecorder auditRecorder;
    private final AuditLogRepository auditLogRepository;
    private final PlaybookRepository playbookRepository;
    private final IncidentRepository incidentRepository;
    private final PlaybookExecutionRepository playbookExecutionRepository;
    private final WorkflowTriggerPort workflowTriggerPort;
    private final WorkflowStatusPort workflowStatusPort;

    public void restartAgent(UUID assetId, String confirmHostname, String reason, ActorContext actor) {
        Asset asset = requireActionableAsset(assetId, confirmHostname, reason,
                AuditAction.WAZUH_AGENT_RESTART_REQUESTED);

        try {
            agentControlPort.restart(asset.getExternalId());
            recordAttempt(AuditAction.WAZUH_AGENT_RESTART_REQUESTED, asset, actor, reason, true, null);
        } catch (SocConnectorException e) {
            recordAttempt(AuditAction.WAZUH_AGENT_RESTART_REQUESTED, asset, actor, reason, false, e.getMessage());
            throw e;
        }
    }

    /**
     * Active-response {@code firewall-drop} — bloque {@code ipAddress} sur
     * le pare-feu local de l'agent ciblé. Mêmes garde-fous que le
     * redémarrage, plafond horaire compté SÉPARÉMENT (action d'audit
     * distincte) : les deux actions ne se partagent pas leur quota.
     */
    public void blockIp(UUID assetId, String confirmHostname, String ipAddress, String reason, ActorContext actor) {
        Asset asset = requireActionableAsset(assetId, confirmHostname, reason,
                AuditAction.WAZUH_AGENT_FIREWALL_DROP_REQUESTED);
        requireValidIpAddress(ipAddress);

        try {
            agentControlPort.blockIp(asset.getExternalId(), ipAddress.trim());
            recordAttempt(AuditAction.WAZUH_AGENT_FIREWALL_DROP_REQUESTED, asset, actor,
                    reason + "; ip=" + ipAddress.trim(), true, null);
        } catch (SocConnectorException e) {
            recordAttempt(AuditAction.WAZUH_AGENT_FIREWALL_DROP_REQUESTED, asset, actor,
                    reason + "; ip=" + ipAddress.trim(), false, e.getMessage());
            throw e;
        }
    }

    /**
     * Déclenchement manuel d'un workflow Shuffle (ADR-014 phase 5) — mêmes
     * garde-fous que le contrôle d'agents : motif obligatoire, cible
     * confirmée EXPLICITEMENT (le nom du playbook, pas seulement un
     * UUID d'URL), plafond horaire, audit systématique, aucun retry. La
     * cible du plafond est l'INCIDENT, pas le playbook : deux analystes
     * ne doivent pas pouvoir contourner le plafond en visant le même
     * incident via deux playbooks différents... et inversement, deux
     * incidents distincts ne se partagent jamais leur quota.
     */
    public PlaybookExecution triggerShuffleWorkflow(UUID playbookId, UUID incidentId, String confirmPlaybookName,
                                                     String reason, ActorContext actor) {
        Playbook playbook = playbookRepository.findById(playbookId)
                .orElseThrow(() -> new ResourceNotFoundException("Playbook", playbookId.toString()));
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId.toString()));

        requireReason(reason);
        requirePlaybookNameConfirmation(playbook, confirmPlaybookName);
        requireLinkedToShuffle(playbook);
        requireUnderRateCap(TARGET_TYPE_INCIDENT, incidentId.toString(), AuditAction.SHUFFLE_WORKFLOW_TRIGGER_REQUESTED);

        WorkflowTriggerPayload payload = new WorkflowTriggerPayload(
                SEVERITY_TO_SHUFFLE.getOrDefault(incident.getSeverity(), 0),
                incident.getTitle(), incident.getReference(), incident.getOpenedAt().toString(),
                incident.getId().toString());

        try {
            String externalExecutionId = workflowTriggerPort.trigger(playbook.getShuffleWebhookPath(), payload);
            PlaybookExecution execution = playbookExecutionRepository.save(PlaybookExecution.startExternal(
                    playbook.getId(), playbook.getVersion(), playbook.getName(), incidentId, externalExecutionId));
            recordAttempt(AuditAction.SHUFFLE_WORKFLOW_TRIGGER_REQUESTED, TARGET_TYPE_INCIDENT, incidentId.toString(),
                    actor, reason + "; executionId=" + externalExecutionId, true, null);
            return execution;
        } catch (SocConnectorException e) {
            PlaybookExecution execution = playbookExecutionRepository.save(PlaybookExecution.startExternalFailed(
                    playbook.getId(), playbook.getVersion(), playbook.getName(), incidentId, e.getMessage()));
            recordAttempt(AuditAction.SHUFFLE_WORKFLOW_TRIGGER_REQUESTED, TARGET_TYPE_INCIDENT, incidentId.toString(),
                    actor, reason, false, e.getMessage());
            return execution;
        }
    }

    /**
     * Réconciliation en lecture (ADR-014 phase 5) : contrairement au
     * déclenchement, une consultation est idempotente — rejouable sans
     * risque, aucun garde-fou de plafond ni d'audit ici. Un no-op sur une
     * exécution déjà terminale ou non externe (jamais d'exception : cette
     * consultation peut être appelée à tout moment par l'écran de suivi).
     */
    public PlaybookExecution refreshShuffleWorkflowStatus(UUID executionId) {
        PlaybookExecution execution = playbookExecutionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("PlaybookExecution", executionId.toString()));
        if (execution.getExternalExecutionId() == null
                || !isReconcilable(execution.getStatus())) {
            return execution;
        }
        Playbook playbook = playbookRepository.findById(execution.getPlaybookId())
                .orElseThrow(() -> new ResourceNotFoundException("Playbook", execution.getPlaybookId().toString()));

        WorkflowExecutionStatus status = workflowStatusPort.statusOf(
                playbook.getShuffleWorkflowId(), execution.getExternalExecutionId());
        switch (status.outcome()) {
            case SUCCEEDED -> execution.completeExternally(status.resultSummary());
            case FAILED -> execution.partialFailure(status.resultSummary());
            case NOT_FOUND -> execution.markOrphaned();
            case STILL_RUNNING -> { /* aucun changement : reste IN_PROGRESS */ }
        }
        return playbookExecutionRepository.save(execution);
    }

    private static boolean isReconcilable(ExecutionStatus status) {
        return status == ExecutionStatus.IN_PROGRESS || status == ExecutionStatus.ORPHANED;
    }

    private static void requirePlaybookNameConfirmation(Playbook playbook, String confirmPlaybookName) {
        String normalized = TextNormalization.blankToNull(confirmPlaybookName);
        if (normalized == null || !playbook.getName().trim().equalsIgnoreCase(normalized.trim())) {
            throw new BusinessRuleViolationException("ACTION_TARGET_NOT_CONFIRMED",
                    "The confirmed playbook name does not match — action refused");
        }
    }

    private static void requireLinkedToShuffle(Playbook playbook) {
        if (!playbook.isLinkedToShuffleWorkflow()) {
            throw new BusinessRuleViolationException("PLAYBOOK_NOT_LINKED_TO_SHUFFLE",
                    "This playbook is not linked to a Shuffle workflow");
        }
    }

    private Asset requireActionableAsset(UUID assetId, String confirmHostname, String reason, AuditAction action) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", assetId.toString()));

        requireReason(reason);
        requireHostnameConfirmation(asset, confirmHostname);
        requireWazuhManaged(asset);
        requireUnderRateCap(TARGET_TYPE_ASSET, assetId.toString(), action);
        return asset;
    }

    private static void requireValidIpAddress(String ipAddress) {
        String normalized = TextNormalization.blankToNull(ipAddress);
        if (normalized == null || !IPV4_FORMAT.matcher(normalized.trim()).matches()) {
            throw new BusinessRuleViolationException("ACTION_INVALID_IP_ADDRESS",
                    "A valid IPv4 address is required to block");
        }
    }

    private static void requireReason(String reason) {
        if (TextNormalization.blankToNull(reason) == null) {
            throw new BusinessRuleViolationException("ACTION_REASON_REQUIRED",
                    "A reason is required before acting on a real agent");
        }
    }

    /**
     * La cible doit être NOMMÉE par l'appelant, pas seulement identifiée
     * par un UUID d'URL qu'un clic mal placé peut porter par erreur.
     */
    private static void requireHostnameConfirmation(Asset asset, String confirmHostname) {
        String normalized = TextNormalization.blankToNull(confirmHostname);
        if (normalized == null || !asset.getHostname().equals(TextNormalization.lowerTrim(normalized))) {
            throw new BusinessRuleViolationException("ACTION_TARGET_NOT_CONFIRMED",
                    "The confirmed hostname does not match this asset — action refused");
        }
    }

    private static void requireWazuhManaged(Asset asset) {
        if (!"wazuh".equals(asset.getExternalSource()) || asset.getExternalId() == null) {
            throw new BusinessRuleViolationException("ACTION_NOT_WAZUH_MANAGED",
                    "This asset is not managed by the Wazuh connector — no agent to act on");
        }
    }

    private void requireUnderRateCap(String targetType, String targetId, AuditAction action) {
        Instant now = Instant.now();
        long recentAttempts = auditLogRepository.search(new AuditLogQuery(
                        action, null, now.minus(RATE_WINDOW), now,
                        targetType, targetId, PageQuery.of(0, 1)))
                .totalElements();
        if (recentAttempts >= MAX_ACTIONS_PER_HOUR) {
            throw new BusinessRuleViolationException("ACTION_RATE_LIMIT_EXCEEDED",
                    "Too many attempts of this action on this target in the last hour (max %d)"
                            .formatted(MAX_ACTIONS_PER_HOUR));
        }
    }

    private void recordAttempt(AuditAction action, Asset asset, ActorContext actor, String reason,
                               boolean succeeded, String error) {
        recordAttempt(action, TARGET_TYPE_ASSET, asset.getId().toString(), actor, reason, succeeded, error);
    }

    private void recordAttempt(AuditAction action, String targetType, String targetId, ActorContext actor,
                               String reason, boolean succeeded, String error) {
        String details = "reason=%s; outcome=%s%s".formatted(
                reason, succeeded ? "SUCCESS" : "FAILURE", error == null ? "" : "; error=" + error);
        auditRecorder.record(action, actor.username(), actor.userId(),
                targetType, targetId, details, actor.ipAddress());
    }
}
