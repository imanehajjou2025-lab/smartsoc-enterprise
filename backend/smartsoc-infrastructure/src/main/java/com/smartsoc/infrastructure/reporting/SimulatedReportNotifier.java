package com.smartsoc.infrastructure.reporting;

import com.smartsoc.application.reporting.ReportNotifier;
import com.smartsoc.domain.reporting.Report;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stub du notifieur (mode simulation, ADR-005) : la plateforme se démontre
 * de bout en bout sans serveur SMTP réel — journalise ce qui aurait été
 * envoyé plutôt que de l'envoyer.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "smartsoc.notifications.mode",
        havingValue = NotificationsProperties.MODE_SIMULATION, matchIfMissing = true)
public class SimulatedReportNotifier implements ReportNotifier {

    @Override
    public void notifyGenerated(Report report, String recipientEmail) {
        log.info("[simulation] Report '{}' ({}) would be emailed to {}",
                report.getTitle(), report.getId(), recipientEmail);
    }
}
