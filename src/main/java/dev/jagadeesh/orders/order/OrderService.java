package dev.jagadeesh.orders.order;

import dev.jagadeesh.orders.common.ConflictException;
import dev.jagadeesh.orders.common.NotFoundException;
import dev.jagadeesh.orders.product.Product;
import dev.jagadeesh.orders.product.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final StockService stock;
    private final Clock clock;

    public OrderService(OrderRepository orders, StockService stock, Clock clock) {
        this.orders = orders;
        this.stock = stock;
        this.clock = clock;
    }

    public record Placed(Order order, boolean created) {
    }

    /**
     * Reserves stock line by line with conditional updates. If any line can't be reserved, the lines
     * already taken are released before the error is returned (a compensating action), so a failed order
     * never leaves stock missing. This avoids multi-document transactions, which need a replica set.
     */
    public Placed place(PlaceOrderRequest req, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Order> existing = orders.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return new Placed(existing.get(), false);
            }
        }
        Map<String, Integer> lines = mergeLines(req.items());
        List<OrderItem> items = new ArrayList<>();
        try {
            for (Map.Entry<String, Integer> line : lines.entrySet()) {
                Product p = stock.reserve(line.getKey(), line.getValue());
                BigDecimal lineTotal = p.getPrice().multiply(BigDecimal.valueOf(line.getValue()));
                items.add(new OrderItem(p.getSku(), p.getName(), p.getCategory(), p.getPrice(), line.getValue(), lineTotal));
            }
            return new Placed(orders.insert(new Order(req.customerId(), idempotencyKey, items, clock.instant())), true);
        } catch (DuplicateKeyException e) {
            // two retries with the same key raced; the other one won, so give back our stock and return its order
            releaseAll(items);
            return new Placed(orders.findByIdempotencyKey(idempotencyKey).orElseThrow(), false);
        } catch (RuntimeException e) {
            releaseAll(items);
            throw e;
        }
    }

    public Order get(String id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("No order " + id));
    }

    public Page<Order> forCustomer(String customerId, OrderStatus status, Pageable page) {
        return status == null ? orders.findByCustomerId(customerId, page)
                : orders.findByCustomerIdAndStatus(customerId, status, page);
    }

    public Order changeStatus(String id, OrderStatus target) {
        Order order = get(id);
        if (!order.getStatus().canMoveTo(target)) {
            throw new ConflictException("ILLEGAL_TRANSITION",
                    "Order " + id + " is " + order.getStatus() + " and cannot become " + target);
        }
        boolean restock = target == OrderStatus.CANCELLED && order.getStatus().releasesStockOnCancel();
        order.moveTo(target, clock.instant());
        Order saved = orders.save(order);    // @Version: a concurrent status change fails instead of being lost
        if (restock) {
            releaseAll(saved.getItems());
        }
        return saved;
    }

    /** The same SKU twice in one request becomes one line, so it is reserved in a single update. */
    private static Map<String, Integer> mergeLines(List<PlaceOrderRequest.Line> lines) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        lines.forEach(l -> merged.merge(l.sku(), l.quantity(), Integer::sum));
        return merged;
    }

    private void releaseAll(List<OrderItem> items) {
        for (OrderItem i : items) {
            try {
                stock.release(i.sku(), i.quantity());
            } catch (RuntimeException ex) {
                log.error("Could not release {} x {}; stock needs a manual fix", i.quantity(), i.sku(), ex);
            }
        }
    }
}
