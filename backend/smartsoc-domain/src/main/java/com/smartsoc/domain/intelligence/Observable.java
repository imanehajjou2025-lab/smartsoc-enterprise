package com.smartsoc.domain.intelligence;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.DomainException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Observable relevé dans un événement de sécurité — l'adresse, le
 * domaine, l'URL ou le hash qu'une alerte cite explicitement.
 *
 * <p><b>Pourquoi cette classe vit dans le contexte intelligence.</b> Un
 * observable et un indicateur sont les deux faces d'une même
 * comparaison : ils se rapprochent sur le couple (type, valeur
 * normalisée). Les loger ensemble garantit qu'ils partagent le MÊME
 * vocabulaire de types et, surtout, la MÊME normalisation — celle de
 * {@link IndicatorType#normalize(String)}, appelée ici, jamais recopiée.
 * Deux normalisations distinctes finiraient par diverger, et la
 * corrélation raterait sans lever la moindre erreur. La dépendance ne va
 * que dans un sens : une alerte connaît ce vocabulaire, l'intelligence
 * ignore tout des alertes.
 *
 * <p>L'égalité porte sur le couple complet : c'est exactement la clé de
 * jointure côté base.
 */
public record Observable(IndicatorType type, String value) {

    private static final String INVALID = "INVALID_OBSERVABLE";

    /**
     * Nombre maximal d'observables retenus pour une alerte. Le domaine se
     * borne lui-même plutôt que de compter sur la validation de l'API :
     * un producteur qui joindrait des milliers d'observables à chaque
     * événement ferait gonfler la table de corrélation sans rien apporter
     * à l'analyse.
     */
    public static final int MAX_PER_ALERT = 100;

    public Observable {
        if (type == null) {
            throw new BusinessRuleViolationException(INVALID,
                    "An observable must have a type");
        }
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException(INVALID,
                    "An observable must have a value");
        }
    }

    /**
     * Construit un observable en NORMALISANT sa valeur par les règles de
     * son type. Lève si la valeur n'est pas du type annoncé.
     */
    public static Observable of(IndicatorType type, String rawValue) {
        if (type == null) {
            throw new BusinessRuleViolationException(INVALID,
                    "An observable must have a type");
        }
        return new Observable(type, type.normalize(rawValue));
    }

    /** Observable tel qu'un producteur le déclare, avant normalisation. */
    public record Raw(IndicatorType type, String value) {
    }

    /**
     * Observable écarté, décrit pour que le producteur puisse corriger.
     *
     * <p>{@code code} est la partie CONTRACTUELLE : stable, dérivée du
     * type annoncé, faite pour être testée par une intégration.
     * {@code message} est informatif et peut évoluer — un producteur qui
     * l'analyserait se lierait à une formulation, pas à une règle.
     */
    public record Rejection(int index, IndicatorType type, String value,
                            String code, String message) {
    }

    /** Code de rejet : {@code INVALID_SHA256}, {@code INVALID_DOMAIN}… */
    private static String rejectionCode(IndicatorType type) {
        return type == null ? INVALID : "INVALID_" + type.name();
    }

    /** Ce qui est retenu, et ce qui est écarté avec son motif. */
    public record ParseResult(List<Observable> accepted, List<Rejection> rejected) {
    }

    /**
     * Lecture TOLÉRANTE d'une liste déclarée : les observables valides
     * sont retenus, les autres écartés et nommés.
     *
     * <p>Une alerte est une pièce d'evidence : la perdre parce qu'un de
     * ses observables est mal formé serait un très mauvais échange. Le
     * producteur apprend ce qui a été écarté par la réponse d'ingestion,
     * sans qu'aucune détection ne disparaisse entre-temps. C'est la même
     * doctrine que la tolérance par élément des lots CTI (ADR-009).
     *
     * <p>Les doublons sont fondus : deux déclarations qui se normalisent
     * pareil désignent le même observable.
     */
    public static ParseResult parseTolerant(List<Raw> declared) {
        if (declared == null || declared.isEmpty()) {
            return new ParseResult(List.of(), List.of());
        }
        Set<Observable> accepted = new LinkedHashSet<>();
        List<Rejection> rejected = new ArrayList<>();

        for (int index = 0; index < declared.size(); index++) {
            Raw raw = declared.get(index);
            if (raw == null) {
                rejected.add(new Rejection(index, null, null, INVALID,
                        "Observable entry is null"));
                continue;
            }
            if (accepted.size() >= MAX_PER_ALERT) {
                rejected.add(new Rejection(index, raw.type(), raw.value(), "TOO_MANY_OBSERVABLES",
                        "At most %d observables are kept per alert".formatted(MAX_PER_ALERT)));
                continue;
            }
            try {
                accepted.add(of(raw.type(), raw.value()));
            } catch (DomainException e) {
                rejected.add(new Rejection(index, raw.type(), raw.value(),
                        rejectionCode(raw.type()), e.getMessage()));
            }
        }
        return new ParseResult(List.copyOf(accepted), List.copyOf(rejected));
    }
}
