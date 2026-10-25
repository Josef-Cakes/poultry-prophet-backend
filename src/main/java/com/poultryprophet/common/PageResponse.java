package com.poultryprophet.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable API representation for a bounded page. Do not expose Spring Data's
 * Page implementation directly because its JSON shape can change between
 * framework versions.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext
) {
    public static <S, T> PageResponse<T> from(Page<S> source, Function<S, T> mapper) {
        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.hasPrevious(),
                source.hasNext()
        );
    }
}
