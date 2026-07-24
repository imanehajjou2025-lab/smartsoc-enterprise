package com.smartsoc.api.mitre.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Contrats REST du contexte MITRE ATT&CK. */
public final class MitreDtos {

    /**
     * Plafond d'un import de catalogue. ATT&CK Enterprise complet compte
     * environ un millier de techniques et sous-techniques : un bundle
     * entier passe donc en une requête, mais borné pour rejeter un abus
     * AVANT toute désérialisation.
     */
    public static final int MAX_IMPORT_SIZE = 2000;

    private MitreDtos() {
    }

    /** Une tactique de la matrice — identifiée par son shortName ATT&CK. */
    public record MitreTacticResponse(String attackId, String shortName, String name) {
    }

    /**
     * Une technique du catalogue. {@code tactics} porte les shortNames
     * ATT&CK, dans l'ordre des colonnes de la matrice — le frontend joint
     * dessus pour construire la heatmap.
     */
    public record MitreTechniqueResponse(
            String attackId,
            boolean subTechnique,
            String parentId,
            String name,
            String description,
            String url,
            List<String> tactics,
            boolean deprecated,
            String attackVersion) {
    }

    /**
     * Une technique telle qu'un bundle ATT&CK la pousse. Les tactiques sont
     * des shortNames STIX ({@code execution}, {@code command-and-control}) :
     * un shortName inconnu est écarté côté serveur, et une entrée sans
     * aucune tactique reconnue est rejetée et nommée dans le compte rendu.
     */
    public record MitreImportTechnique(
            @NotBlank @Size(max = 9) String attackId,
            @NotBlank @Size(max = 256) String name,
            String description,
            @Size(max = 512) String url,
            List<@NotBlank @Size(max = 100) String> tactics,
            boolean deprecated) {
    }

    /** Le bundle s'identifie une fois par sa version ATT&CK. */
    public record MitreImportRequest(
            @Size(max = 20) String attackVersion,
            @NotEmpty @Size(max = MAX_IMPORT_SIZE) List<@Valid @NotNull MitreImportTechnique> techniques) {
    }

    /**
     * Compte rendu d'un import. Les techniques valides sont importées même
     * si d'autres sont rejetées : un bundle réel peut contenir des entrées
     * inexploitables, et refuser le lot entier priverait la matrice de tout
     * le reste. Chaque rejet est nommé pour que la source soit corrigée.
     */
    public record MitreImportReportResponse(
            int received,
            int created,
            int updated,
            int rejected,
            List<MitreImportError> errors) {
    }

    public record MitreImportError(
            int index,
            String attackId,
            String code,
            String message) {
    }
}
