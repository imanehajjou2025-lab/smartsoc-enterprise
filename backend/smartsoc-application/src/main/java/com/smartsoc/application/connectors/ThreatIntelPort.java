package com.smartsoc.application.connectors;

import com.smartsoc.application.intelligence.IndicatorFeedIngestionService.FeedObservation;

import java.util.List;

/**
 * Port du flux CTI MISP (ADR-014 phase 2). Réutilise directement
 * {@link FeedObservation} — la forme exacte attendue par
 * {@code IndicatorFeedIngestionService}, déjà construite pour le webhook
 * de push {@code /api/v1/ingest/iocs} — au lieu d'inventer un second
 * type : c'est exactement pour ce cas que l'ingestion par lot existait
 * déjà en tolérance par élément.
 *
 * <p>Une seule méthode pour tout le flux (comme
 * {@code VulnerabilityFeedPort}) : MISP répond en un aller pour
 * l'ensemble des attributs demandés, pas par agent/objet.
 */
public interface ThreatIntelPort {

    List<FeedObservation> listIndicators();
}
