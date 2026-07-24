package com.smartsoc.domain.mitre;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.TextNormalization;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Une technique (ou sous-technique) de la matrice MITRE ATT&CK — le
 * « comment » d'un comportement adverse.
 *
 * <p><b>Deux natures strictement séparées, comme pour un IOC :</b>
 * <ul>
 *   <li>l'<b>IDENTITÉ</b> — l'identifiant ATT&CK normalisé
 *       ({@code T1059}, {@code T1059.001}). Finale : c'est la clé de
 *       corrélation avec les techniques brutes portées par les alertes
 *       ({@code Alert.mitreTechniques}). Un ré-import ne la touche jamais ;</li>
 *   <li>les <b>MÉTADONNÉES</b> — nom, description, URL, tactiques,
 *       dépréciation, version ATT&CK. Volatiles : chaque import du
 *       catalogue les rafraîchit.</li>
 * </ul>
 *
 * <p><b>Pas de suppression.</b> Une technique dépréciée par ATT&CK reste
 * dans le catalogue : les alertes historiques qui la référencent doivent
 * continuer à s'enrichir de son nom et de ses tactiques — même doctrine
 * que l'IOC périmé ou l'actif décommissionné, consultables à jamais.
 * {@code deprecated} suit l'import : c'est un fait DU référentiel ATT&CK,
 * pas une décision d'analyste (là est la différence avec la révocation
 * d'un IOC, qui elle survit au flux).
 *
 * <p>La provenance reste externe à la plateforme (ADR-005) : le catalogue
 * est semé puis rafraîchi par import, SmartSOC n'interroge aucun service
 * ATT&CK au runtime.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MitreTechnique {

    private static final String INVALID = "INVALID_MITRE_TECHNIQUE";
    private static final int MAX_NAME_LENGTH = 256;

    // ---------- IDENTITÉ (immuable) ----------
    private final UUID id;
    private final String attackId;
    private final boolean subTechnique;
    private final String parentId;

    // ---------- MÉTADONNÉES (rafraîchies par l'import) ----------
    private String name;
    private String description;
    private String url;
    private Set<MitreTactic> tactics;
    private boolean deprecated;
    private String attackVersion;

    /**
     * Entrée de catalogue — une technique telle qu'un bundle ATT&CK (ou un
     * import manuel, le même acte pour le domaine) la décrit. Porte
     * l'identité ET les métadonnées ; seule la première est figée.
     */
    @Builder
    public record CatalogEntry(
            String attackId,
            String name,
            String description,
            String url,
            Set<MitreTactic> tactics,
            boolean deprecated,
            String attackVersion) {
    }

    /** Point d'entrée unique : une technique naît d'une entrée de catalogue. */
    public static MitreTechnique fromCatalog(CatalogEntry entry) {
        if (entry == null) {
            throw new BusinessRuleViolationException(INVALID,
                    "A catalog entry is required");
        }
        String normalizedId = MitreTechniqueId.normalize(entry.attackId());
        return MitreTechnique.builder()
                .id(UUID.randomUUID())
                .attackId(normalizedId)
                .subTechnique(MitreTechniqueId.isSubTechnique(normalizedId))
                .parentId(MitreTechniqueId.parentId(normalizedId).orElse(null))
                .name(requireName(entry.name()))
                .description(TextNormalization.blankToNull(entry.description()))
                .url(TextNormalization.blankToNull(entry.url()))
                .tactics(requireTactics(entry.tactics()))
                .deprecated(entry.deprecated())
                .attackVersion(TextNormalization.blankToNull(entry.attackVersion()))
                .build();
    }

    /**
     * Ré-import : l'upsert du catalogue. Rafraîchit les MÉTADONNÉES et rien
     * d'autre. L'identité est vérifiée d'abord — une entrée qui ne porte
     * pas le même identifiant normalisé parle d'une autre technique.
     */
    public void refreshFrom(CatalogEntry entry) {
        requireSameIdentity(entry);
        this.name = requireName(entry.name());
        this.description = TextNormalization.blankToNull(entry.description());
        this.url = TextNormalization.blankToNull(entry.url());
        this.tactics = requireTactics(entry.tactics());
        // La dépréciation est un fait du référentiel ATT&CK : l'import fait foi.
        this.deprecated = entry.deprecated();
        this.attackVersion = TextNormalization.blankToNull(entry.attackVersion());
    }

    private void requireSameIdentity(CatalogEntry entry) {
        if (entry == null || !this.attackId.equals(safeNormalize(entry.attackId()))) {
            throw new BusinessRuleViolationException("MITRE_TECHNIQUE_IDENTITY_MISMATCH",
                    "A catalog entry cannot change the identity of technique %s"
                            .formatted(this.attackId));
        }
    }

    private static String safeNormalize(String attackId) {
        try {
            return MitreTechniqueId.normalize(attackId);
        } catch (BusinessRuleViolationException e) {
            return null;
        }
    }

    private static String requireName(String name) {
        String cleaned = TextNormalization.blankToNull(name);
        if (cleaned == null) {
            throw new BusinessRuleViolationException(INVALID,
                    "A technique must have a name");
        }
        if (cleaned.length() > MAX_NAME_LENGTH) {
            throw new BusinessRuleViolationException(INVALID,
                    "A technique name must not exceed %d characters".formatted(MAX_NAME_LENGTH));
        }
        return cleaned;
    }

    /** Une technique appartient à au moins une tactique — c'est sa place dans la matrice. */
    private static Set<MitreTactic> requireTactics(Set<MitreTactic> tactics) {
        EnumSet<MitreTactic> copy = EnumSet.noneOf(MitreTactic.class);
        if (tactics != null) {
            for (MitreTactic tactic : tactics) {
                if (tactic != null) {
                    copy.add(tactic);
                }
            }
        }
        if (copy.isEmpty()) {
            throw new BusinessRuleViolationException(INVALID,
                    "A technique must belong to at least one tactic");
        }
        return Collections.unmodifiableSet(copy);
    }
}
