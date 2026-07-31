package com.smartsoc.api.settings;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Horodatage de démarrage, pour le panneau « À propos » (uptime réel). */
@Component
class PlatformStartupTracker {

    private volatile Instant startedAt;

    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        startedAt = Instant.now();
    }

    Instant startedAt() {
        return startedAt;
    }
}
