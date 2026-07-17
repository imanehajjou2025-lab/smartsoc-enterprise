package com.smartsoc.api.realtime;

import com.smartsoc.api.alerts.AlertApiMapper;
import com.smartsoc.application.ai.AlertClassifiedEvent;
import com.smartsoc.application.alerts.AlertIngestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Relaie la vie des alertes vers la console en temps réel. Deux topics aux
 * sémantiques distinctes : /topic/alerts = nouvelle alerte ingérée,
 * /topic/alerts/updates = alerte existante modifiée (ex. verdict IA).
 * Le payload STOMP est le même AlertResponse que l'API REST : un seul
 * contrat côté frontend.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertRealtimePublisher {

    public static final String TOPIC_ALERTS = "/topic/alerts";
    public static final String TOPIC_ALERT_UPDATES = "/topic/alerts/updates";

    private final SimpMessagingTemplate messagingTemplate;
    private final AlertApiMapper mapper;

    @EventListener
    public void onAlertIngested(AlertIngestedEvent event) {
        messagingTemplate.convertAndSend(TOPIC_ALERTS, mapper.toResponse(event.alert()));
        log.debug("Alert {} published to {}", event.alert().getId(), TOPIC_ALERTS);
    }

    @EventListener
    public void onAlertClassified(AlertClassifiedEvent event) {
        messagingTemplate.convertAndSend(TOPIC_ALERT_UPDATES, mapper.toResponse(event.alert()));
        log.debug("Alert {} update published to {}", event.alert().getId(), TOPIC_ALERT_UPDATES);
    }
}
