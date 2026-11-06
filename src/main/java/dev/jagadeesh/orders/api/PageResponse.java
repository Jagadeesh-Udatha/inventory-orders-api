package dev.jagadeesh.orders.api;

import org.springframework.data.domain.Page;

import java.util.List;

/** A stable JSON shape for pages (Spring's PageImpl serialisation is not a stable contract). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }
}
