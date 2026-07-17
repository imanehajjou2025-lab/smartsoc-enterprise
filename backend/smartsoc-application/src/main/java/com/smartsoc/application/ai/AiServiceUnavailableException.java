package com.smartsoc.application.ai;

/**
 * Levée uniquement sur une demande EXPLICITE de classification (endpoint
 * manuel) quand le classifieur est indisponible : l'appelant doit savoir
 * que rien ne s'est passé. Le flux automatique d'ingestion, lui, dégrade
 * silencieusement (l'alerte reste sans score).
 */
public class AiServiceUnavailableException extends RuntimeException {

    private final String code;

    public AiServiceUnavailableException(String message) {
        super(message);
        this.code = "AI_UNAVAILABLE";
    }

    public String getCode() {
        return code;
    }
}
