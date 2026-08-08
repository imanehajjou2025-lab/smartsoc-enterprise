package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Jeton JWT Wazuh en cache — mesuré en pratique à ~15 min de validité
 * (ADR-015 : différent du patron IA, qui porte une clé statique sans
 * expiration). Rafraîchi avec une marge de sécurité plutôt que d'attendre
 * un 401 pour réagir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.mode", havingValue = ConnectorProperties.MODE_LIVE)
class WazuhTokenCache {

    /** Marge sous la validité réelle observée (~15 min) : jamais servir un jeton sur le point d'expirer. */
    private static final Duration TOKEN_TTL = Duration.ofMinutes(13);

    private final WazuhAuthClient authClient;

    private volatile String token;
    private volatile Instant expiresAt = Instant.EPOCH;

    synchronized String currentToken() {
        if (Instant.now().isAfter(expiresAt)) {
            refresh();
        }
        return token;
    }

    private void refresh() {
        try {
            WazuhAuthClient.AuthResponse response = authClient.authenticate();
            if (response.data() == null || response.data().token() == null) {
                throw new SocConnectorException("Wazuh authentication response carried no token");
            }
            this.token = response.data().token();
            this.expiresAt = Instant.now().plus(TOKEN_TTL);
            log.debug("Wazuh token refreshed, valid until {}", expiresAt);
        } catch (SocConnectorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SocConnectorException("Wazuh authentication failed: " + e.getMessage(), e);
        }
    }
}
