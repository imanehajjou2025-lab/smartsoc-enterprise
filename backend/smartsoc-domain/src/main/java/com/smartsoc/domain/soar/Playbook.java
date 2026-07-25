package com.smartsoc.domain.soar;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.TextNormalization;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Un playbook — une procédure de réponse documentée, à suivre pas à pas
 * contre un incident (voir {@link PlaybookExecution}). Ce n'est PAS un
 * moteur d'automatisation : l'exécution réelle d'actions externes
 * (bloquer une IP, isoler un poste) reste le rôle de Shuffle, opéré hors
 * de la plateforme (ADR-006, ADR-012) ; ce module ne fait que documenter
 * la procédure et suivre son déroulement par un analyste.
 *
 * <p><b>Versioning léger.</b> {@code version} est un simple compteur
 * incrémenté à chaque modification — pas d'historique séparé. L'audit des
 * exécutions passées ne dépend pas de cet historique : chaque
 * {@link PlaybookExecution} fige sa propre copie des étapes au démarrage
 * ({@code steps_snapshot}), donc une édition ultérieure du gabarit ne
 * change jamais silencieusement une exécution déjà en cours ou terminée.
 *
 * <p><b>Pas de suppression.</b> Un playbook est une connaissance
 * institutionnelle (une procédure de réponse), pas un gabarit de
 * recherche personnel comme une requête de chasse : il s'archive, il ne
 * se supprime jamais.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Playbook {

    private static final String INVALID = "INVALID_PLAYBOOK";
    private static final int MAX_NAME_LENGTH = 200;

    private final UUID id;
    private String name;
    private String description;
    private int version;
    private List<PlaybookStepTemplate> steps;
    private boolean archived;

    /** Définition d'un playbook, telle que déclarée ou mise à jour par un analyste. */
    @Builder
    public record DeclareCommand(String name, String description, List<PlaybookStepTemplate> steps) {
    }

    public static Playbook declare(DeclareCommand command) {
        return Playbook.builder()
                .id(UUID.randomUUID())
                .name(requireName(command.name()))
                .description(TextNormalization.blankToNull(command.description()))
                .version(1)
                .steps(renumber(command.steps()))
                .archived(false)
                .build();
    }

    /** Remplace la définition et incrémente la version (audit : les exécutions passées ne changent jamais). */
    public void update(DeclareCommand command) {
        this.name = requireName(command.name());
        this.description = TextNormalization.blankToNull(command.description());
        this.steps = renumber(command.steps());
        this.version = this.version + 1;
    }

    public void archive() {
        if (archived) {
            throw new BusinessRuleViolationException("PLAYBOOK_ALREADY_ARCHIVED",
                    "This playbook is already archived");
        }
        this.archived = true;
    }

    public List<PlaybookStepTemplate> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    private static String requireName(String name) {
        String cleaned = TextNormalization.blankToNull(name);
        if (cleaned == null) {
            throw new BusinessRuleViolationException(INVALID, "A playbook must have a name");
        }
        if (cleaned.length() > MAX_NAME_LENGTH) {
            throw new BusinessRuleViolationException(INVALID,
                    "A playbook name must not exceed %d characters".formatted(MAX_NAME_LENGTH));
        }
        return cleaned;
    }

    /**
     * L'ordre d'une étape est TOUJOURS dérivé de sa position dans la
     * liste — jamais fait confiance à une valeur fournie par l'appelant
     * (pas de trou, pas de doublon possible, par construction).
     */
    private static List<PlaybookStepTemplate> renumber(List<PlaybookStepTemplate> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new BusinessRuleViolationException(INVALID, "A playbook must have at least one step");
        }
        List<PlaybookStepTemplate> renumbered = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            PlaybookStepTemplate step = steps.get(i);
            if (step == null) {
                throw new BusinessRuleViolationException(INVALID, "A step must not be null");
            }
            renumbered.add(new PlaybookStepTemplate(i, step.title(), step.description()));
        }
        return renumbered;
    }
}
