package dev.jagadeesh.orders.api;

import dev.jagadeesh.orders.order.Order;
import dev.jagadeesh.orders.order.OrderService;
import dev.jagadeesh.orders.order.OrderStatus;
import dev.jagadeesh.orders.order.PlaceOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "Orders", description = "Place orders and move them through their lifecycle")
@Validated
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @Operation(summary = "Place an order; stock is reserved atomically",
            description = "Send an Idempotency-Key header to make retries safe: the same key returns the first order (200) instead of creating another.")
    @PostMapping
    public ResponseEntity<Order> place(@Valid @RequestBody PlaceOrderRequest req,
                                       @Parameter(description = "Client-generated unique key, e.g. a UUID")
                                       @RequestHeader(value = "Idempotency-Key", required = false) @Size(max = 64) String key) {
        OrderService.Placed placed = orders.place(req, key);
        if (!placed.created()) {
            return ResponseEntity.ok(placed.order());
        }
        return ResponseEntity.created(URI.create("/api/orders/" + placed.order().getId())).body(placed.order());
    }

    @Operation(summary = "Get an order")
    @GetMapping("/{id}")
    public Order get(@PathVariable String id) {
        return orders.get(id);
    }

    @Operation(summary = "A customer's orders, newest first, optionally by status")
    @GetMapping
    public PageResponse<Order> list(@RequestParam String customerId,
                                    @RequestParam(required = false) OrderStatus status,
                                    @RequestParam(defaultValue = "0") @Min(0) int page,
                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(orders.forCustomer(customerId, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @Operation(summary = "Move an order to CONFIRMED, SHIPPED, DELIVERED or CANCELLED")
    @PostMapping("/{id}/status")
    public Order changeStatus(@PathVariable String id, @Valid @RequestBody StatusRequest body) {
        return orders.changeStatus(id, body.status());
    }

    @Operation(summary = "Cancel an order and return its stock")
    @PostMapping("/{id}/cancel")
    public Order cancel(@PathVariable String id) {
        return orders.changeStatus(id, OrderStatus.CANCELLED);
    }

    public record StatusRequest(@NotNull OrderStatus status) {
    }
}
