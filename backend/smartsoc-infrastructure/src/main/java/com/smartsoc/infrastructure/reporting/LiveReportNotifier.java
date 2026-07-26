package com.smartsoc.infrastructure.reporting;

import com.smartsoc.application.reporting.ReportNotifier;
import com.smartsoc.domain.reporting.Report;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Notifieur e-mail réel (mode live, ADR-005), via le {@code JavaMailSender}
 * auto-configuré par {@code spring-boot-starter-mail} (propriétés
 * {@code spring.mail.*}, fournies par l'environnement). Une notification
 * manquée (SMTP indisponible…) ne fait jamais échouer la génération du
 * rapport — même dégradation gracieuse que le classifieur IA en mode live.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.notifications.mode", havingValue = NotificationsProperties.MODE_LIVE)
public class LiveReportNotifier implements ReportNotifier {

    private final JavaMailSender mailSender;
    private final NotificationsProperties properties;

    @Override
    public void notifyGenerated(Report report, String recipientEmail) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.fromAddress());
            message.setTo(recipientEmail);
            message.setSubject("SmartSOC — rapport généré : " + report.getTitle());
            message.setText("Le rapport « %s » a été généré et est disponible dans SmartSOC.".formatted(
                    report.getTitle()));
            mailSender.send(message);
        } catch (RuntimeException ex) {
            log.warn("Failed to email report {} to {}: {}", report.getId(), recipientEmail, ex.getMessage());
        }
    }
}
