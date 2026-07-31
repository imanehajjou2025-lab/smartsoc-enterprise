package com.smartsoc.infrastructure.notifications;

import com.smartsoc.application.settings.NotificationTestPort;
import com.smartsoc.infrastructure.reporting.NotificationsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envoi réel d'un e-mail de diagnostic (mode live uniquement, ADR-005).
 * Contrairement à {@code LiveReportNotifier}, l'échec ici n'est PAS
 * avalé : c'est le seul but de l'action (voir {@link NotificationTestPort}).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.notifications.mode", havingValue = NotificationsProperties.MODE_LIVE)
public class LiveNotificationTestSender implements NotificationTestPort {

    private final JavaMailSender mailSender;
    private final NotificationsProperties properties;

    @Override
    public void sendTestEmail(String recipientEmail) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.fromAddress());
            message.setTo(recipientEmail);
            message.setSubject("SmartSOC — e-mail de test");
            message.setText("Ceci est un e-mail de test envoyé depuis la console Paramètres de SmartSOC. "
                    + "Si vous le recevez, l'envoi réel de notifications fonctionne.");
            mailSender.send(message);
        } catch (MailException ex) {
            throw new NotificationTestException("Échec de l'envoi : " + ex.getMostSpecificCause().getMessage(), ex);
        }
    }
}
