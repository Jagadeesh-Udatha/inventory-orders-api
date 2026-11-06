package dev.jagadeesh.orders.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            "PLACED, CONFIRMED, true", "PLACED, CANCELLED, true", "PLACED, SHIPPED, false",
            "CONFIRMED, SHIPPED, true", "CONFIRMED, CANCELLED, true",
            "SHIPPED, DELIVERED, true", "SHIPPED, CANCELLED, false",
            "DELIVERED, CANCELLED, false", "CANCELLED, PLACED, false", "CANCELLED, CONFIRMED, false"})
    void transitions(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.canMoveTo(to)).isEqualTo(allowed);
    }

    @Test
    void onlyUnshippedOrdersGiveStockBack() {
        assertThat(OrderStatus.PLACED.releasesStockOnCancel()).isTrue();
        assertThat(OrderStatus.CONFIRMED.releasesStockOnCancel()).isTrue();
        assertThat(OrderStatus.SHIPPED.releasesStockOnCancel()).isFalse();
    }
}
