package com.smartsoc.application.settings;

import java.time.Instant;
import java.util.Arrays;

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

        // equals/hashCode/toString explicites : le contenu du tableau doit
        // etre compare par valeur, pas par reference (java:S6218).
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof BackupResult that)) {
                return false;
            }
            return Arrays.equals(content, that.content)
                    && filename.equals(that.filename)
                    && generatedAt.equals(that.generatedAt);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(content) * 31 + filename.hashCode() * 17 + generatedAt.hashCode();
        }

        @Override
        public String toString() {
            return "BackupResult[content=%d bytes, filename=%s, generatedAt=%s]"
                    .formatted(content.length, filename, generatedAt);
        }
    }

    /** Levée quand l'export échoue (outil absent, connexion refusée, etc.). */
    class BackupExecutionException extends RuntimeException {
        public BackupExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
