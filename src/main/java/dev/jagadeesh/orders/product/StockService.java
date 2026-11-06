package dev.jagadeesh.orders.product;

import dev.jagadeesh.orders.common.ConflictException;
import dev.jagadeesh.orders.common.NotFoundException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Stock changes as single conditional updates. "Take 3 if at least 3 are left" is one
 * findAndModify, so two buyers can never both take the last unit; no locks or transactions needed.
 */
@Service
public class StockService {

    private final MongoTemplate mongo;

    public StockService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Takes {@code qty} units and returns the product as it was before, or fails with 409 if not enough. */
    public Product reserve(String sku, int qty) {
        Query enough = Query.query(where("sku").is(sku).and("active").is(true).and("stock").gte(qty));
        Product before = mongo.findAndModify(enough, new Update().inc("stock", -qty).inc("version", 1),
                FindAndModifyOptions.options().returnNew(false), Product.class);
        if (before == null) {
            Product p = mongo.findOne(Query.query(where("sku").is(sku)), Product.class);
            if (p == null || !p.isActive()) {
                throw new NotFoundException("No active product with SKU " + sku);
            }
            throw new ConflictException("INSUFFICIENT_STOCK",
                    "Only " + p.getStock() + " left of " + sku + ", requested " + qty);
        }
        return before;
    }

    /** Puts units back (order cancelled, or a later line of the same order failed). */
    public void release(String sku, int qty) {
        mongo.updateFirst(Query.query(where("sku").is(sku)), new Update().inc("stock", qty).inc("version", 1), Product.class);
    }

    public Product restock(String sku, int qty) {
        Product after = mongo.findAndModify(Query.query(where("sku").is(sku)),
                new Update().inc("stock", qty).inc("version", 1),
                FindAndModifyOptions.options().returnNew(true), Product.class);
        if (after == null) {
            throw new NotFoundException("No product with SKU " + sku);
        }
        return after;
    }
}
