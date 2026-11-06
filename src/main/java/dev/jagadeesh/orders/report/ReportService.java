package dev.jagadeesh.orders.report;

import dev.jagadeesh.orders.order.Order;
import dev.jagadeesh.orders.order.OrderStatus;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.unwind;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
public class ReportService {

    private final MongoTemplate mongo;

    public ReportService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * Revenue and units per category for orders created in [from, to), cancelled orders excluded.
     * One aggregation pipeline: $match → $unwind items → $group by category → $sort by revenue.
     */
    public List<CategorySales> salesByCategory(Instant from, Instant to) {
        Aggregation pipeline = Aggregation.newAggregation(
                match(where("createdAt").gte(from).lt(to).and("status").ne(OrderStatus.CANCELLED)),
                unwind("items"),
                group("items.category")
                        .sum("items.quantity").as("unitsSold")
                        .sum("items.lineTotal").as("revenue")
                        .addToSet("_id").as("orderIds"),
                project("unitsSold", "revenue").and("orderIds").size().as("orders"),
                sort(Sort.by(Sort.Direction.DESC, "revenue")));
        return mongo.aggregate(pipeline, Order.class, Document.class).getMappedResults().stream()
                .map(d -> new CategorySales(
                        d.getString("_id"),
                        ((Number) d.get("unitsSold")).longValue(),
                        toBigDecimal(d.get("revenue")),
                        ((Number) d.get("orders")).longValue()))
                .toList();
    }

    private static BigDecimal toBigDecimal(Object v) {
        return v instanceof Decimal128 d ? d.bigDecimalValue() : new BigDecimal(String.valueOf(v));
    }
}
