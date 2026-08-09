package com.smartsoc.application.connectors;

import com.smartsoc.domain.intelligence.IndicatorType;

/**
 * Port de réputation d'observable VirusTotal (ADR-014 phase 3) — à la
 * demande d'un analyste UNIQUEMENT, jamais sur le flux (R4). Un seul
 * observable par appel, contrairement aux ports programmés des autres
 * connecteurs qui répondent en lot.
 */
public interface ObservableReputationPort {

    Lookup lookup(IndicatorType type, String normalizedValue);

    /** Compteurs bruts par catégorie ({@code last_analysis_stats} de VirusTotal) — jamais un verdict déjà tranché. */
    record Lookup(int maliciousCount, int suspiciousCount, int harmlessCount, int undetectedCount) {
    }
}
