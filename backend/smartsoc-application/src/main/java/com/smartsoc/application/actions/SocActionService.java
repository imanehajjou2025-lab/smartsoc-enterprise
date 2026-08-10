package com.smartsoc.application.actions;

import com.smartsoc.application.audit.ActorContext;
import com.smartsoc.application.audit.AuditRecorder;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.domain.assets.Asset;
import com.smartsoc.domain.assets.AssetRepository;
import com.smartsoc.domain.audit.AuditAction;
import com.smartsoc.domain.audit.AuditLogQuery;
import com.smartsoc.domain.audit.AuditLogRepository;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.common.TextNormalization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

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
    private static final int MAX_ACTIONS_PER_HOUR = 3;
    private static final Duration RATE_WINDOW = Duration.ofHours(1);

    private final AssetRepository assetRepository;
    private final AgentControlPort agentControlPort;
    private final AuditRecorder auditRecorder;
    private final AuditLogRepository auditLogRepository;

    public void restartAgent(UUID assetId, String confirmHostname, String reason, ActorContext actor) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", assetId.toString()));

        requireReason(reason);
        requireHostnameConfirmation(asset, confirmHostname);
        requireWazuhManaged(asset);
        requireUnderRateCap(assetId);

        try {
            agentControlPort.restart(asset.getExternalId());
            recordAttempt(asset, actor, reason, true, null);
        } catch (SocConnectorException e) {
            recordAttempt(asset, actor, reason, false, e.getMessage());
            throw e;
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
                    "This asset is not managed by the Wazuh connector — no agent to restart");
        }
    }

    private void requireUnderRateCap(UUID assetId) {
        Instant now = Instant.now();
        long recentAttempts = auditLogRepository.search(new AuditLogQuery(
                        AuditAction.WAZUH_AGENT_RESTART_REQUESTED, null, now.minus(RATE_WINDOW), now,
                        TARGET_TYPE_ASSET, assetId.toString(), PageQuery.of(0, 1)))
                .totalElements();
        if (recentAttempts >= MAX_ACTIONS_PER_HOUR) {
            throw new BusinessRuleViolationException("ACTION_RATE_LIMIT_EXCEEDED",
                    "Too many restart attempts on this asset in the last hour (max %d)"
                            .formatted(MAX_ACTIONS_PER_HOUR));
        }
    }

    private void recordAttempt(Asset asset, ActorContext actor, String reason, boolean succeeded, String error) {
        String details = "reason=%s; outcome=%s%s".formatted(
                reason, succeeded ? "SUCCESS" : "FAILURE", error == null ? "" : "; error=" + error);
        auditRecorder.record(AuditAction.WAZUH_AGENT_RESTART_REQUESTED, actor.username(), actor.userId(),
                TARGET_TYPE_ASSET, asset.getId().toString(), details, actor.ipAddress());
    }
}
