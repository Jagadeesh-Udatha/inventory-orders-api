package dev.jagadeesh.orders.order;

import java.math.BigDecimal;

/** A snapshot of the product at order time, so later price or name changes don't rewrite history. */
public record OrderItem(String sku, String name, String category, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
}
