package com.smartsoc.application.connectors;

import com.smartsoc.domain.connectors.ConnectorDescriptor;

/**
 * Décrit les capacités réelles de VirusTotal (ADR-014 §6.5,
 * {@code CapabilityProbe} de {@code CONNECTORS-REFERENCE.md} §1).
 *
 * <p>VirusTotal n'a pas de notion de version côté client : c'est un
 * service SaaS public toujours à jour, sans endpoint de version exposé
 * (contrat v3 vérifié). Le descripteur le dit explicitement plutôt que
 * de laisser un champ vide muet — jamais une valeur inventée.
 */
public interface VirusTotalCapabilityPort {

    ConnectorDescriptor detect(ConnectorDescriptor previous);
}
