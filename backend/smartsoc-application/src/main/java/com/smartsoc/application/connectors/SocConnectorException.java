package com.smartsoc.application.connectors;

/**
 * Échec d'un appel à un connecteur SOC (réseau, timeout, TLS,
 * authentification, circuit ouvert…) — ADR-014. Pour les connecteurs
 * PROGRAMMÉS, toujours attrapée par l'orchestrateur de synchronisation,
 * jamais laissée remonter jusqu'à l'API : la dégradation gracieuse est
 * une règle d'architecture, pas un cas d'erreur. Seule exception : les
 * connecteurs À LA DEMANDE (VirusTotal, phase 3) — sans donnée en
 * cache à servir, l'échec remonte jusqu'à {@code GlobalExceptionHandler}
 * (503) plutôt que d'être avalé silencieusement.
 */
public class SocConnectorException extends RuntimeException {

    public SocConnectorException(String message) {
        super(message);
    }

    public SocConnectorException(String message, Throwable cause) {
        super(message, cause);
    }
}
