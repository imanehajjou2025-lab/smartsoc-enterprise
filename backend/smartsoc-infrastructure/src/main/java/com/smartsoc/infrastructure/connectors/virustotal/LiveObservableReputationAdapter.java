package com.smartsoc.infrastructure.connectors.virustotal;

import com.smartsoc.application.connectors.ObservableReputationPort;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Adaptateur live du port de réputation (ADR-014 phase 3) : appelle la
 * vraie API VirusTotal via Feign, protégé par circuit breaker ET
 * limiteur de débit (4 requêtes/min en offre gratuite — voir
 * {@code application.yml}) — le SEUL connecteur de la plateforme à
 * empiler les deux, parce que c'est le seul dont le quota est assez
 * strict pour bloquer le compte entier en cas d'abus.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.connectors.virustotal.mode", havingValue = ConnectorProperties.MODE_LIVE)
public class LiveObservableReputationAdapter implements ObservableReputationPort {

    static final String CIRCUIT_BREAKER = "virusTotalReputation";
    static final String RATE_LIMITER = "virusTotalReputation";

    private final VirusTotalClient client;
    private final VirusTotalReputationMapper mapper;

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "lookupUnavailable")
    @RateLimiter(name = RATE_LIMITER, fallbackMethod = "lookupUnavailable")
    public Lookup lookup(IndicatorType type, String normalizedValue) {
        VirusTotalReportResponse response = switch (type) {
            case IPV4, IPV6 -> client.getIpAddress(normalizedValue);
            case DOMAIN -> client.getDomain(normalizedValue);
            case URL -> client.getUrl(urlId(normalizedValue));
            case MD5, SHA1, SHA256 -> client.getFile(normalizedValue);
            case EMAIL -> throw new BusinessRuleViolationException("UNSUPPORTED_OBSERVABLE_TYPE",
                    "VirusTotal does not analyze email addresses");
        };
        return mapper.toLookup(response);
    }

    /** Identifiant VirusTotal d'une URL : base64 URL-safe SANS remplissage de l'URL complète. */
    private static String urlId(String url) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(url.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unused") // invoqué par Resilience4j (circuit breaker et limiteur de débit)
    private Lookup lookupUnavailable(IndicatorType type, String normalizedValue, Throwable cause) {
        log.warn("VirusTotal reputation lookup unavailable for {}:{}: {}", type, normalizedValue, cause.getMessage());
        throw new SocConnectorException("VirusTotal reputation lookup unavailable: " + cause.getMessage(), cause);
    }
}
