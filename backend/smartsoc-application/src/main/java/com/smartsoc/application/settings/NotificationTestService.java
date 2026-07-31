package com.smartsoc.application.settings;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * {@link NotificationTestPort} n'est câblé qu'en mode live (ADR-005,
 * même doctrine que le classifieur IA) : en simulation, aucun bean n'existe
 * et l'action est refusée explicitement plutôt que de prétendre avoir
 * envoyé un e-mail qui n'a jamais existé.
 */
@Service
public class NotificationTestService {

    private final Optional<NotificationTestPort> testPort;

    public NotificationTestService(Optional<NotificationTestPort> testPort) {
        this.testPort = testPort;
    }

    public void sendTestEmail(String recipientEmail) {
        NotificationTestPort port = testPort.orElseThrow(() -> new BusinessRuleViolationException(
                "NOTIFICATIONS_MODE_SIMULATION",
                "Les notifications sont en mode simulation : aucun e-mail réel n'est envoyé"));
        port.sendTestEmail(recipientEmail);
    }
}
