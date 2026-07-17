package com.smartsoc.application.ai;

import com.smartsoc.domain.alerts.Alert;

import java.util.Optional;

/**
 * Port vers le classifieur TP/FP externe (ADR-008). Deux adaptateurs en
 * infrastructure, sélectionnés par `smartsoc.ai.mode` : simulation (défaut)
 * ou client Feign vers le vrai service.
 *
 * Contrat du port : Optional.empty() = classifieur indisponible (panne,
 * timeout, circuit ouvert…). L'adaptateur ne propage jamais d'exception —
 * la dégradation gracieuse est une règle d'architecture, pas un cas d'erreur.
 */
public interface AlertClassifier {

    Optional<AlertClassification> classify(Alert alert);
}
