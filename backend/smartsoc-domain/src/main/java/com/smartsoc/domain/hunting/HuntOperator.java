package com.smartsoc.domain.hunting;

/** Opérateur de comparaison d'une condition de chasse. Chaque {@link HuntField} déclare les opérateurs qu'il accepte. */
public enum HuntOperator {
    EQUALS,
    CONTAINS,
    GREATER_THAN,
    LESS_THAN
}
