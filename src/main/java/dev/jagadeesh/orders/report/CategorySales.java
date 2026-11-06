package dev.jagadeesh.orders.report;

import java.math.BigDecimal;

public record CategorySales(String category, long unitsSold, BigDecimal revenue, long orders) {
}
