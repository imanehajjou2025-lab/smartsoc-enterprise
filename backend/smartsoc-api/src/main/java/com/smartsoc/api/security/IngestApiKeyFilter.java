package com.smartsoc.api.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authentifie TOUS les webhooks d'ingestion par clé d'API (header
 * X-API-Key) : alertes et indicateurs CTI aujourd'hui, et tout futur
 * endpoint sous {@code /api/v1/ingest/} sans modification ici.
 * Comparaison en temps constant (MessageDigest.isEqual) : pas d'oracle de
 * timing. En cas d'échec, la requête continue anonyme et se fait refuser
 * par la règle hasRole(INGEST) — 401 RFC 9457 comme le reste de l'API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestApiKeyFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final IngestProperties properties;

    @PostConstruct
    void warnIfDisabled() {
        if (!properties.enabled()) {
            log.warn("SMARTSOC_INGEST_API_KEY is not set: every /api/v1/ingest/** endpoint "
                    + "is disabled (alerts and CTI indicators).");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/ingest/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(API_KEY_HEADER);
        if (properties.enabled() && provided != null && constantTimeEquals(properties.apiKey(), provided)) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "soc-ingest", null, List.of(new SimpleGrantedAuthority("ROLE_INGEST")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
