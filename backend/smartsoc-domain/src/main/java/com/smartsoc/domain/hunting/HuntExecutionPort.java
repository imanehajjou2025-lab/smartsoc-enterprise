package com.smartsoc.domain.hunting;

import com.smartsoc.domain.common.PageQuery;

/**
 * Port d'exécution d'une chasse — traduit un {@link HuntGroup} en
 * correspondances sur les alertes. Deux adaptateurs prévus, même doctrine
 * que le classifieur IA (ADR-008) et le catalogue MITRE : {@code
 * simulation} (PostgreSQL, sur les alertes déjà ingérées — seul livré en
 * V1) et {@code live} (OpenSearch via les connecteurs, chemin déjà décidé
 * par ADR-004, différé jusqu'à disposer d'un schéma d'index réel plutôt que
 * deviné).
 *
 * <p>{@code huntId} n'est PAS un paramètre : ce port ignore si les
 * critères viennent d'une requête sauvegardée ou d'une exécution ad hoc —
 * c'est à l'appelant (couche application) d'attribuer l'identité au
 * résultat via {@link HuntExecutionResult#withHuntId(java.util.UUID)}.
 */
public interface HuntExecutionPort {

    HuntExecutionResult execute(HuntGroup criteria, PageQuery page);
}
