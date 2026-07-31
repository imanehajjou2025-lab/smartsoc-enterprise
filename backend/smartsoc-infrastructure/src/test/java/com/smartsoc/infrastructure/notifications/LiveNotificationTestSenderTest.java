package com.smartsoc.infrastructure.notifications;

import com.smartsoc.application.settings.NotificationTestPort.NotificationTestException;
import com.smartsoc.infrastructure.reporting.NotificationsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aucun serveur SMTP réel dans les tests (ADR-005) : on vise un port fermé
 * en local avec un délai court, ce qui exerce le vrai chemin d'échec de
 * {@code JavaMailSender} sans dépendre d'un service externe.
 */
class LiveNotificationTestSenderTest {

    @Test
    void sendTestEmailWrapsAMailFailureIntoANotificationTestException() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(1);
        Properties props = new Properties();
        props.put("mail.smtp.connectiontimeout", "500");
        props.put("mail.smtp.timeout", "500");
        mailSender.setJavaMailProperties(props);

        LiveNotificationTestSender sender = new LiveNotificationTestSender(
                mailSender, new NotificationsProperties(NotificationsProperties.MODE_LIVE, "smartsoc@localhost"));

        assertThatThrownBy(() -> sender.sendTestEmail("admin@smartsoc.local"))
                .isInstanceOf(NotificationTestException.class);
    }
}
