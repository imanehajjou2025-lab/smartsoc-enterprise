package com.smartsoc.domain.connectors;

import java.time.Instant;
import java.util.Set;

/**
 * Version et capacités RÉELLEMENT détectées d'un connecteur (ADR-014 §6.5),
 * jamais supposées. Né d'un cas concret : l'origine des données de
 * vulnérabilité Wazuh dépend de la version déployée (API sur les versions
 * anciennes, Indexer sur les récentes) — un connecteur qui ne connaît pas
 * la version ne peut pas choisir le bon chemin.
 *
 * @param detectedVersion chaîne brute rapportée par l'outil (ex. {@code "Wazuh v4.12.0"}),
 *                        {@code null} tant qu'aucune sonde n'a réussi
 * @param capabilities    ce que cette version supporte réellement ; une capacité
 *                        absente est déclarée « indisponible », jamais masquée
 * @param detectedAt      quand cette version a été observée pour la dernière fois
 */
public record ConnectorDescriptor(
        String detectedVersion,
        Set<ConnectorCapability> capabilities,
        Instant detectedAt) {

    public ConnectorDescriptor {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    public static ConnectorDescriptor unknown() {
        return new ConnectorDescriptor(null, Set.of(), null);
    }

    public boolean supports(ConnectorCapability capability) {
        return capabilities.contains(capability);
    }
}
