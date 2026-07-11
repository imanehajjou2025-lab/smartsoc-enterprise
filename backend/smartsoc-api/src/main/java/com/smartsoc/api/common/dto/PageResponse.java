package com.smartsoc.api.common.dto;

import com.smartsoc.domain.common.PageResult;

import java.util.List;
import java.util.function.Function;

/** Enveloppe de pagination uniforme de toutes les listes de l'API. */
public record PageResponse<T>(
        List<T> items,
        long totalElements,
        int page,
        int size,
        int totalPages) {

    public static <D, T> PageResponse<T> of(PageResult<D> result, Function<D, T> mapper) {
        return new PageResponse<>(
                result.items().stream().map(mapper).toList(),
                result.totalElements(),
                result.page(),
                result.size(),
                result.totalPages());
    }
}
