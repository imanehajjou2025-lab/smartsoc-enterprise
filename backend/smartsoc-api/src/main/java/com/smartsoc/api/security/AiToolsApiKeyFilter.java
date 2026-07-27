package com.smartsoc.api.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
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
 * Authentifie le service d'assistant IA externe (ADR-008) par clé d'API
 * (header X-API-Key), UNIQUEMENT sur les requêtes GET : ses outils lisent
 * des données existantes (alertes, MITRE...) en lecture seule, jamais
 * d'écriture (ADR-003). Aucune règle supplémentaire n'est nécessaire dans
 * SecurityConfig : les endpoints GET déjà exposés ne demandent qu'un
 * principal authentifié, et les endpoints d'écriture exigent déjà des rôles
 * (ADMIN/SOC_MANAGER/SOC_ANALYST) que cette clé n'obtient jamais.
 * Comparaison en temps constant (MessageDigest.isEqual) : pas d'oracle de
 * timing. En cas d'échec, la requête continue anonyme et se fait refuser
 * par la règle .authenticated() — 401 RFC 9457 comme le reste de l'API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiToolsApiKeyFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final AiToolsProperties properties;

    @PostConstruct
    void warnIfDisabled() {
        if (!properties.enabled()) {
            log.warn("SMARTSOC_AI_TOOLS_API_KEY is not set: the AI assistant service cannot "
                    + "authenticate to read platform data (alerts, MITRE...).");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.GET.matches(request.getMethod())
                || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(API_KEY_HEADER);
        if (properties.enabled() && provided != null && constantTimeEquals(properties.apiKey(), provided)) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "soc-ai-tools", null, List.of(new SimpleGrantedAuthority("ROLE_AI_TOOLS")));
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
