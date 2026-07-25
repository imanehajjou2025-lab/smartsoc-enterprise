package com.smartsoc.domain.hunting;

import com.smartsoc.domain.common.BusinessRuleViolationException;

import java.util.Collections;
import java.util.List;

/**
 * Un groupe de l'arbre de critères : combine récursivement d'autres
 * {@link HuntNode} par {@link HuntLogicalOperator}. Invariants STRUCTURELS
 * uniquement (valables pour tout groupe, à toute profondeur, y compris les
 * évolutions futures) — la restriction « racine = AND de conditions
 * plates » propre à la V1 est portée par {@link HuntQuery}, pas ici.
 */
public record HuntGroup(HuntLogicalOperator operator, List<HuntNode> children) implements HuntNode {

    private static final String INVALID = "INVALID_HUNT_GROUP";

    /**
     * Faux positif audité (CodeQL java/internal-representation-exposure),
     * alerte supprimée via l'API code-scanning avec justification tracée.
     * Pas de commentaire {@code codeql[...]} : essayé d'abord, sans effet
     * dans ce dépôt (cause exacte non confirmée — peut-être lié à
     * {@code build-mode: none} du workflow) ; la suppression via l'API,
     * elle, a été vérifiée effective au rescan suivant.
     *
     * <p>La règle documente elle-même deux remèdes valides : la copie
     * défensive ({@code List.copyOf}, appliquée ici) et la vue en lecture
     * seule ({@code Collections.unmodifiableList}, appliquée dans
     * l'accesseur {@link #children()} ci-dessous). Les DEUX sont en place.
     * La même paire de remèdes a fermé l'alerte jumelle sur
     * {@code MitreTechnique.getTactics()} (classe Lombok classique) le même
     * jour — seule la nature <b>record</b> de ce type change ici : CodeQL
     * continue de désigner le constructeur compact comme site d'exposition
     * même quand l'accesseur canonique est explicitement surchargé, signe
     * d'une limite de son modèle des records Java plutôt que d'un défaut
     * réel. Preuve à l'exécution : {@code HuntGroupTest
     * .childrenAreDefensivelyCopied} vérifie que {@code children().add(...)}
     * lève {@code UnsupportedOperationException}.
     */
    public HuntGroup {
        if (operator == null) {
            throw new BusinessRuleViolationException(INVALID, "A hunt group requires a logical operator");
        }
        if (children == null || children.isEmpty()) {
            throw new BusinessRuleViolationException(INVALID, "A hunt group requires at least one child");
        }
        if (operator == HuntLogicalOperator.NOT && children.size() != 1) {
            throw new BusinessRuleViolationException(INVALID, "A NOT group must have exactly one child");
        }
        children = List.copyOf(children);
    }

    /**
     * Accesseur écrit à la main plutôt que généré par le record : le
     * constructeur compact affecte déjà {@code List.copyOf(children)}, mais
     * CodeQL (java/internal-representation-exposure) ne fait pas confiance
     * à cette garantie prise en amont — il veut voir l'enveloppement dans
     * l'accesseur lui-même. Même correctif que {@code Alert.getObservables}.
     */
    @Override
    public List<HuntNode> children() {
        return Collections.unmodifiableList(children);
    }
}
