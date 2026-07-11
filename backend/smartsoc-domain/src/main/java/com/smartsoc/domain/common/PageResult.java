package com.smartsoc.domain.common;

import java.util.List;
import java.util.function.Function;

/** Résultat paginé, indépendant du framework. */
public record PageResult<T>(List<T> items, long totalElements, int page, int size) {

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), totalElements, page, size);
    }
}
