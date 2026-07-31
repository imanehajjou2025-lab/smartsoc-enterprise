package com.smartsoc.api.settings;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Ping de disponibilité des services IA externes (console Paramètres,
 * panneau Santé des services). Volontairement indépendant des clients
 * Feign (qui n'existent qu'en mode live, voir {@code AiLiveConfig}) : ce
 * contrôle doit pouvoir répondre quel que soit {@code smartsoc.ai.mode}.
 */
@Component
class AiHealthChecker {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    enum Status {
        UP, DOWN, NOT_CONFIGURED
    }

    Status check(String baseUrl, String healthPath) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return Status.NOT_CONFIGURED;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + healthPath))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 400 ? Status.UP : Status.DOWN;
        } catch (IOException ex) {
            return Status.DOWN;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Status.DOWN;
        }
    }
}
