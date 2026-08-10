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
 * Jeton JWT du compte d'ACTIONS ({@code smartsoc-actuator}) en cache —
 * SÉPARÉ de {@link WazuhTokenCache} (compte lecture {@code smartsoc-reader}) :
 * deux identités, deux jetons, jamais partagés. Même durée de validité
 * mesurée en réel (~15 min, ADR-015).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.actions.mode", havingValue = ConnectorProperties.MODE_LIVE)
class WazuhActionsTokenCache {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(13);

    private final WazuhActionsAuthClient authClient;

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
                throw new SocConnectorException("Wazuh actions authentication response carried no token");
            }
            this.token = response.data().token();
            this.expiresAt = Instant.now().plus(TOKEN_TTL);
            log.debug("Wazuh actions token refreshed, valid until {}", expiresAt);
        } catch (SocConnectorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SocConnectorException("Wazuh actions authentication failed: " + e.getMessage(), e);
        }
    }
}
