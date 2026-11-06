package dev.jagadeesh.orders.order;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("orders")
@CompoundIndex(name = "customer_created", def = "{'customerId': 1, 'createdAt': -1}")
public class Order {

    @Id
    private String id;
    private String customerId;
    /** Client-supplied Idempotency-Key; unique+sparse so a retried POST finds the first order instead of creating a second. */
    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;
    private List<OrderItem> items;
    private BigDecimal total;
    @Indexed
    private OrderStatus status;
    private List<StatusChange> history = new ArrayList<>();
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
    @Version
    private Long version;

    public record StatusChange(OrderStatus status, Instant at) {
    }

    protected Order() {
    }

    Order(String customerId, String idempotencyKey, List<OrderItem> items, Instant now) {
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.items = List.copyOf(items);
        this.total = items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.status = OrderStatus.PLACED;
        this.history.add(new StatusChange(OrderStatus.PLACED, now));
    }

    void moveTo(OrderStatus target, Instant now) {
        this.status = target;
        this.history.add(new StatusChange(target, now));
    }

    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public List<OrderItem> getItems() { return items; }
    public BigDecimal getTotal() { return total; }
    public OrderStatus getStatus() { return status; }
    public List<StatusChange> getHistory() { return history; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
