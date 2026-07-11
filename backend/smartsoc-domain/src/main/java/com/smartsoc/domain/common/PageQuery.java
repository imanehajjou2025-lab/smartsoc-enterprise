package com.smartsoc.domain.common;

/**
 * Pagination indépendante du framework (le domaine ne connaît pas
 * Spring Data). Page 0-indexée ; taille bornée pour protéger la base.
 */
public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 200;

    public PageQuery {
        if (page < 0) {
            throw new BusinessRuleViolationException("INVALID_PAGE", "Page index must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessRuleViolationException("INVALID_PAGE",
                    "Page size must be between 1 and %d".formatted(MAX_SIZE));
        }
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size);
    }
}
