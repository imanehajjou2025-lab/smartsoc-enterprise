package com.smartsoc.application.settings;

import java.time.Instant;

/**
 * Port de sauvegarde de la base PostgreSQL (console Paramètres, ADMIN
 * uniquement). Une seule opération volontairement : produire un instantané
 * téléchargeable. La restauration n'est pas construite dans cette
 * itération — une opération destructrice de cette nature mérite son propre
 * examen, pas un bouton ajouté en passant.
 */
public interface DatabaseBackupPort {

    BackupResult exportDatabase();

    /**
     * @param content   contenu brut du dump (format {@code pg_dump} personnalisé)
     * @param filename  nom de fichier suggéré au téléchargement
     * @param generatedAt horodatage de génération
     */
    record BackupResult(byte[] content, String filename, Instant generatedAt) {
    }

    /** Levée quand l'export échoue (outil absent, connexion refusée, etc.). */
    class BackupExecutionException extends RuntimeException {
        public BackupExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
