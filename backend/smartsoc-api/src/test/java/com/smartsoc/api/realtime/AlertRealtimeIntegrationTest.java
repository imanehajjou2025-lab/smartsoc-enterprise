package com.smartsoc.api.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.api.alerts.dto.AlertDtos.AlertResponse;
import com.smartsoc.api.identity.auth.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Temps réel de bout en bout : un client STOMP authentifié par JWT
 * s'abonne à /topic/alerts et reçoit l'alerte poussée par le webhook
 * d'ingestion. Un CONNECT sans token est refusé.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
                "smartsoc.security.ingest.api-key=test-ingest-key-0123456789abcdef",
        })
@Import(TestcontainersConfiguration.class)
class AlertRealtimeIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void subscriberReceivesIngestedAlertInRealtime() throws Exception {
        StompSession session = connect(adminToken());
        BlockingQueue<AlertResponse> received = new LinkedBlockingQueue<>();
        session.subscribe(AlertRealtimePublisher.TOPIC_ALERTS, new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return AlertResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.offer((AlertResponse) payload);
            }
        });

        String externalId = "evt-rt-" + UUID.randomUUID();
        ingest(externalId);

        AlertResponse alert = received.poll(10, TimeUnit.SECONDS);
        assertThat(alert).as("l'alerte doit arriver en temps réel").isNotNull();
        assertThat(alert.externalId()).isEqualTo(externalId);
        assertThat(alert.severity().name()).isEqualTo("CRITICAL");

        // Replay du même événement : aucune nouvelle publication.
        ingest(externalId);
        assertThat(received.poll(2, TimeUnit.SECONDS))
                .as("un replay ne doit pas être republié").isNull();

        session.disconnect();
    }

    @Test
    void connectWithoutTokenIsRejected() {
        assertThatThrownBy(() -> connect(null))
                .isInstanceOf(ExecutionException.class);
    }

    // --- helpers ---

    private String adminToken() {
        return rest.postForEntity("/api/v1/auth/login",
                        Map.of("username", "admin", "password", "IntegrationTest123!"),
                        TokenResponse.class)
                .getBody().accessToken();
    }

    private StompSession connect(String bearerToken) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        client.setMessageConverter(converter);

        StompHeaders connectHeaders = new StompHeaders();
        if (bearerToken != null) {
            connectHeaders.add("Authorization", "Bearer " + bearerToken);
        }
        return client.connectAsync("ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);
    }

    private void ingest(String externalId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", "test-ingest-key-0123456789abcdef");
        rest.postForEntity("/api/v1/ingest/alerts", new HttpEntity<>(Map.of(
                "source", "wazuh", "externalId", externalId,
                "title", "Realtime test alert",
                "severity", "CRITICAL", "detectedAt", "2026-07-11T10:00:00Z",
                "mitreTechniques", List.of("T1110")), headers), String.class);
    }
}
