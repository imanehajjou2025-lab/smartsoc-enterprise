package com.smartsoc.application.connectors;

/**
 * Échec d'un appel à un connecteur SOC (réseau, timeout, TLS,
 * authentification, circuit ouvert…) — ADR-014. Toujours attrapée par
 * l'orchestrateur de synchronisation, jamais laissée remonter jusqu'à
 * l'API : la dégradation gracieuse est une règle d'architecture, pas un
 * cas d'erreur.
 */
public class SocConnectorException extends RuntimeException {

    public SocConnectorException(String message) {
        super(message);
    }

    public SocConnectorException(String message, Throwable cause) {
        super(message, cause);
    }
}
