package dev.jagadeesh.orders.product;

import java.math.BigDecimal;

/** Optional filters for GET /api/products; null means "don't filter on this". */
public record ProductSearch(String q, String category, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock) {
}
