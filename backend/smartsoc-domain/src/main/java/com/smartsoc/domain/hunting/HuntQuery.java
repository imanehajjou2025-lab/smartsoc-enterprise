package com.smartsoc.domain.hunting;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.TextNormalization;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Une requête de chasse sauvegardée — un gabarit de recherche nommé sur les
 * alertes déjà ingérées. Sans état d'exécution persisté : chaque exécution
 * est un calcul à la lecture (voir {@link HuntExecutionPort}), le champ
 * {@link #lastExecutedAt} n'étant qu'un repère d'usage récent, pas une
 * trace d'audit.
 *
 * <p><b>Restriction V1 de {@link #criteria}.</b> {@link HuntGroup} et
 * {@link HuntNode} sont déjà l'arbre complet AND/OR/NOT imbriqué ; cette
 * classe restreint pour l'instant la racine à un {@code AND} de
 * {@link HuntCondition} plates ({@link #requireV1Shape}). Débloquer
 * l'imbrication plus tard est un changement de VALIDATION ici, jamais du
 * schéma JSONB ni du contrat API.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class HuntQuery {

    private static final String INVALID = "INVALID_HUNT_QUERY";
    private static final int MAX_NAME_LENGTH = 200;

    private final UUID id;
    private String name;
    private String description;
    private HuntGroup criteria;
    private HuntVisibility visibility;
    private Instant lastExecutedAt;

    /** Définition d'une requête de chasse, telle que déclarée ou mise à jour par un analyste. */
    @Builder
    public record DeclareCommand(String name, String description, HuntGroup criteria, HuntVisibility visibility) {
    }

    public static HuntQuery declare(DeclareCommand command) {
        requireCommand(command);
        requireV1Shape(command.criteria());
        return HuntQuery.builder()
                .id(UUID.randomUUID())
                .name(requireName(command.name()))
                .description(TextNormalization.blankToNull(command.description()))
                .criteria(command.criteria())
                .visibility(command.visibility() == null ? HuntVisibility.PRIVATE : command.visibility())
                .lastExecutedAt(null)
                .build();
    }

    /** Remplace intégralement la définition — même acte que la déclaration, sur une identité déjà fixée. */
    public void update(DeclareCommand command) {
        requireCommand(command);
        requireV1Shape(command.criteria());
        this.name = requireName(command.name());
        this.description = TextNormalization.blankToNull(command.description());
        this.criteria = command.criteria();
        this.visibility = command.visibility() == null ? HuntVisibility.PRIVATE : command.visibility();
    }

    /** Repère d'usage récent, mis à jour comme effet de bord d'une exécution — pas un acte d'écriture métier. */
    public void markExecuted(Instant executedAt) {
        this.lastExecutedAt = Objects.requireNonNull(executedAt, "executedAt");
    }

    private static void requireCommand(DeclareCommand command) {
        if (command == null || command.criteria() == null) {
            throw new BusinessRuleViolationException(INVALID, "A hunt query requires criteria");
        }
    }

    private static String requireName(String name) {
        String cleaned = TextNormalization.blankToNull(name);
        if (cleaned == null) {
            throw new BusinessRuleViolationException(INVALID, "A hunt query must have a name");
        }
        if (cleaned.length() > MAX_NAME_LENGTH) {
            throw new BusinessRuleViolationException(INVALID,
                    "A hunt query name must not exceed %d characters".formatted(MAX_NAME_LENGTH));
        }
        return cleaned;
    }

    /**
     * Restriction V1 : la racine doit être {@code AND}, et chaque enfant
     * direct doit être une {@link HuntCondition} — aucun groupe imbriqué,
     * aucun {@code OR}/{@code NOT} en racine pour l'instant.
     */
    private static void requireV1Shape(HuntGroup criteria) {
        if (criteria.operator() != HuntLogicalOperator.AND) {
            throw new BusinessRuleViolationException("HUNT_LOGICAL_OPERATOR_UNSUPPORTED",
                    "Only AND is supported at the root for now (OR/NOT are a planned evolution)");
        }
        for (HuntNode child : criteria.children()) {
            if (!(child instanceof HuntCondition)) {
                throw new BusinessRuleViolationException("HUNT_NESTED_GROUPS_UNSUPPORTED",
                        "Nested groups are not supported yet (planned evolution)");
            }
        }
    }
}
