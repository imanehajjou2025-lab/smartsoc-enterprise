package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Configuration LOCALE au client d'authentification des ACTIONS — Basic
 * Auth avec {@code smartsoc.connectors.wazuh.actions.username/password}
 * (compte {@code smartsoc-actuator}), jamais le compte de lecture
 * {@code wazuh.username/password} (voir {@link WazuhAuthClientConfig}).
 */
class WazuhActionsAuthClientConfig {

    @Bean
    RequestInterceptor wazuhActionsBasicAuthInterceptor(ConnectorProperties properties) {
        return template -> {
            ConnectorProperties.Wazuh wazuh = properties.wazuh();
            ConnectorProperties.Wazuh.Actions actions = wazuh == null ? null : wazuh.actions();
            if (actions == null || isBlank(actions.username()) || isBlank(actions.password())) {
                return;
            }
            String credentials = actions.username() + ":" + actions.password();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            template.header("Authorization", "Basic " + encoded);
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
