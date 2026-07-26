package com.smartsoc.application.reporting;

import com.smartsoc.domain.reporting.Report;

/**
 * Port de notification à la génération d'un rapport (ADR-005 : « e-mail,
 * Teams, Slack derrière des ports »). Un seul adaptateur construit en v1 —
 * e-mail — sélectionné par {@code smartsoc.notifications.mode} : simulation
 * (défaut, journalise) ou live (SMTP réel), même patron que
 * {@link com.smartsoc.application.ai.AlertClassifier}. Teams/Slack restent
 * des extensions possibles de ce port, non construites faute de webhook réel
 * à intégrer aujourd'hui.
 *
 * <p>Contrat : n'échoue jamais bruyamment — une notification manquée ne doit
 * pas faire échouer la génération du rapport elle-même.
 */
public interface ReportNotifier {

    void notifyGenerated(Report report, String recipientEmail);
}
