package dev.jagadeesh.orders.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {

    Optional<Order> findByIdempotencyKey(String key);

    Page<Order> findByCustomerId(String customerId, Pageable page);

    Page<Order> findByCustomerIdAndStatus(String customerId, OrderStatus status, Pageable page);
}
