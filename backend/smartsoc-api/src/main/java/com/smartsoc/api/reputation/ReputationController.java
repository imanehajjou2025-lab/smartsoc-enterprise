package com.smartsoc.api.reputation;

import com.smartsoc.api.reputation.dto.ReputationDtos.ReputationResponse;
import com.smartsoc.application.reputation.ObservableReputationService;
import com.smartsoc.domain.intelligence.IndicatorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Réputation d'un observable, à la demande d'un analyste UNIQUEMENT
 * (ADR-014 phase 3 — VirusTotal, jamais sur le flux, R4). Réservé aux
 * rôles d'écriture bien que ce soit un {@code GET} : chaque appel
 * consomme le quota externe réel (4 requêtes/min, 500/jour), un
 * VIEWER ne doit pas pouvoir l'épuiser.
 */
@RestController
@RequestMapping("/api/v1/reputation")
@PreAuthorize("hasAnyRole('ADMIN','SOC_MANAGER','SOC_ANALYST')")
@RequiredArgsConstructor
public class ReputationController {

    private final ObservableReputationService service;
    private final ReputationApiMapper mapper;

    @GetMapping
    public ReputationResponse getReputation(
            @RequestParam IndicatorType type,
            @RequestParam String value) {
        return mapper.toResponse(service.getReputation(type, value));
    }
}
