package com.smartsoc.application.reputation;

import com.smartsoc.application.connectors.ObservableReputationPort;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.application.connectors.VirusTotalCapabilityPort;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.connectors.ConnectorType;
import com.smartsoc.domain.connectors.SocConnector;
import com.smartsoc.domain.connectors.SocConnectorRepository;
import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.domain.reputation.ObservableReputation;
import com.smartsoc.domain.reputation.ObservableReputationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Réputation d'un observable à la demande d'un analyste (ADR-014 phase 3)
 * — jamais sur le flux (R4). Sert le cache s'il est encore frais,
 * n'appelle VirusTotal que pour un relevé absent ou périmé : c'est le
 * mécanisme qui protège le quota strict (4 req/min, 500/jour), pas une
 * simple optimisation de latence.
 */
@Slf4j
@Service
public class ObservableReputationService {

    private static final String SOURCE = "virustotal";

    /**
     * VirusTotal ne couvre pas les adresses e-mail (contrainte du
     * connecteur, ADR-014 CONNECTORS-REFERENCE §3.4) — rejeté ici aussi,
     * pas seulement masqué côté bouton frontend : une requête directe à
     * l'API ne doit jamais silencieusement échouer contre la source.
     */
    private static final IndicatorType UNSUPPORTED_TYPE = IndicatorType.EMAIL;

    private final ObservableReputationPort port;
    private final ObservableReputationRepository repository;
    private final SocConnectorRepository connectorRepository;
    private final VirusTotalCapabilityPort capabilityPort;
    private final Duration cacheTtl;

    public ObservableReputationService(ObservableReputationPort port,
                                       ObservableReputationRepository repository,
                                       SocConnectorRepository connectorRepository,
                                       VirusTotalCapabilityPort capabilityPort,
                                       @Value("${smartsoc.connectors.virustotal.cache-ttl-hours:24}") long cacheTtlHours) {
        this.port = port;
        this.repository = repository;
        this.connectorRepository = connectorRepository;
        this.capabilityPort = capabilityPort;
        this.cacheTtl = Duration.ofHours(cacheTtlHours);
    }

    /**
     * {@code noRollbackFor} : sur l'échec sans cache, {@link #recordFailure}
     * écrit l'état DISCONNECTED du connecteur puis l'exception est
     * relancée pour que l'appelant (contrôleur) le sache — sans cette
     * annotation, le rollback transactionnel par défaut sur toute
     * exception non contrôlée annulerait cette écriture, même bug de
     * classe que celui déjà rencontré et corrigé sur la rotation des
     * jetons de rafraîchissement (voir {@code InvalidRefreshTokenException}).
     */
    @Transactional(noRollbackFor = SocConnectorException.class)
    public ObservableReputation getReputation(IndicatorType type, String rawValue) {
        if (type == UNSUPPORTED_TYPE) {
            throw new BusinessRuleViolationException("UNSUPPORTED_OBSERVABLE_TYPE",
                    "VirusTotal does not analyze email addresses");
        }
        String normalized = type.normalize(rawValue);

        Optional<ObservableReputation> cached = repository.findByIdentity(SOURCE, type, normalized);
        Instant now = Instant.now();
        if (cached.isPresent() && !cached.get().isStaleAt(now.minus(cacheTtl))) {
            return cached.get();
        }

        ObservableReputationPort.Lookup lookup;
        try {
            lookup = port.lookup(type, normalized);
        } catch (SocConnectorException e) {
            recordFailure(e.getMessage());
            if (cached.isPresent()) {
                // Dégradation gracieuse : un relevé périmé reste plus utile qu'aucune réponse.
                log.warn("VirusTotal lookup failed for {}:{}, serving stale cache from {}: {}",
                        type, normalized, cached.get().getCheckedAt(), e.getMessage());
                return cached.get();
            }
            throw e;
        }

        ObservableReputation.Lookup domainLookup = ObservableReputation.Lookup.builder()
                .source(SOURCE)
                .type(type)
                .value(normalized)
                .maliciousCount(lookup.maliciousCount())
                .suspiciousCount(lookup.suspiciousCount())
                .harmlessCount(lookup.harmlessCount())
                .undetectedCount(lookup.undetectedCount())
                .checkedAt(now)
                .build();

        ObservableReputation reputation = cached
                .map(existing -> {
                    existing.refreshFrom(domainLookup);
                    return existing;
                })
                .orElseGet(() -> ObservableReputation.record(domainLookup));

        reputation = repository.save(reputation);
        recordSuccess();
        return reputation;
    }

    private void recordSuccess() {
        SocConnector connector = connectorRepository.findByType(ConnectorType.VIRUSTOTAL)
                .orElseGet(() -> SocConnector.notConfigured(ConnectorType.VIRUSTOTAL));
        connector.recordSuccess(Instant.now(), capabilityPort.detect(connector.getDescriptor()));
        connectorRepository.save(connector);
    }

    private void recordFailure(String error) {
        SocConnector connector = connectorRepository.findByType(ConnectorType.VIRUSTOTAL)
                .orElseGet(() -> SocConnector.notConfigured(ConnectorType.VIRUSTOTAL));
        connector.recordFailure(Instant.now(), error);
        connectorRepository.save(connector);
    }
}
