package com.smartsoc.domain.connectors;

import java.util.List;
import java.util.Optional;

/**
 * Port de persistance de l'état des connecteurs. Un seul enregistrement
 * par {@link ConnectorType} — {@link #save} fait office d'upsert côté
 * adaptateur.
 */
public interface SocConnectorRepository {

    SocConnector save(SocConnector connector);

    Optional<SocConnector> findByType(ConnectorType type);

    /** Vue d'ensemble pour la section « Connecteurs » de la console. */
    List<SocConnector> findAll();
}
