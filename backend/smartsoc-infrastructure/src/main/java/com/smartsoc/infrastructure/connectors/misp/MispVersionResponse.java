package com.smartsoc.infrastructure.connectors.misp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Modèle BRUT de {@code POST /servers/getVersion} — d'après l'échantillon
 * réel capturé le 2026-08-10
 * ({@code docs/integration/fixtures/misp/get-version-sample.json}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MispVersionResponse(String version) {
}
