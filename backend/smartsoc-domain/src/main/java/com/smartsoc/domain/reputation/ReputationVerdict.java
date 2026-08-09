package com.smartsoc.domain.reputation;

/**
 * Verdict d'un observable, dérivé des votes moteur d'un scanner de
 * réputation (ADR-014 phase 3). VirusTotal ne renvoie aucun verdict
 * unique sur ses endpoints IP/domaine/URL — seulement des compteurs par
 * catégorie ({@code last_analysis_stats}) — donc ce classement est une
 * TRADUCTION assumée (pire cas l'emporte : un seul moteur qui signale
 * malveillant suffit à classer l'observable ainsi), pas une donnée brute.
 */
public enum ReputationVerdict {
    MALICIOUS,
    SUSPICIOUS,
    HARMLESS,
    /** Aucun moteur n'a rendu de verdict sur cet observable. */
    UNDETECTED
}
