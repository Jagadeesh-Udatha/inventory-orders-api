package dev.jagadeesh.orders.order;

import java.util.EnumSet;
import java.util.Set;

/** Order lifecycle. Each status lists where it may go next; anything else is rejected with 409. */
public enum OrderStatus {
    PLACED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED;

    public Set<OrderStatus> next() {
        return switch (this) {
            case PLACED -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(SHIPPED, CANCELLED);
            case SHIPPED -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canMoveTo(OrderStatus target) {
        return next().contains(target);
    }

    /** Stock goes back on the shelf only if the goods never left the warehouse. */
    public boolean releasesStockOnCancel() {
        return this == PLACED || this == CONFIRMED;
    }
}
