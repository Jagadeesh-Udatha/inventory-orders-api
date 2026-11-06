package dev.jagadeesh.orders.product;

import dev.jagadeesh.orders.common.ConflictException;
import dev.jagadeesh.orders.common.NotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
public class ProductService {

    private final ProductRepository products;
    private final MongoTemplate mongo;

    public ProductService(ProductRepository products, MongoTemplate mongo) {
        this.products = products;
        this.mongo = mongo;
    }

    public Product create(ProductRequest r) {
        Product p = new Product(r.sku(), r.name().trim(), normalizeCategory(r.category()), r.price(), r.stock());
        if (Boolean.FALSE.equals(r.active())) {
            p.updateDetails(p.getName(), p.getCategory(), p.getPrice(), false);
        }
        try {
            return products.insert(p);
        } catch (DuplicateKeyException e) {
            throw new ConflictException("DUPLICATE_SKU", "A product with SKU " + r.sku() + " already exists");
        }
    }

    /** Updates catalogue details. Stock is not touched here; it changes only through restock and orders. */
    public Product update(String sku, ProductRequest r) {
        Product p = get(sku);
        if (!p.getSku().equals(r.sku())) {
            throw new ConflictException("SKU_IMMUTABLE", "SKU cannot be changed");
        }
        p.updateDetails(r.name().trim(), normalizeCategory(r.category()), r.price(), r.active() == null || r.active());
        return products.save(p);   // @Version turns a concurrent edit into OptimisticLockingFailureException (409)
    }

    public Product get(String sku) {
        return products.findBySku(sku).orElseThrow(() -> new NotFoundException("No product with SKU " + sku));
    }

    public Page<Product> search(ProductSearch s, Pageable page) {
        List<Criteria> filters = new ArrayList<>();
        filters.add(where("active").is(true));
        if (s.q() != null && !s.q().isBlank()) {
            // quote the input so user text is matched literally, never interpreted as a regex
            filters.add(where("name").regex(Pattern.quote(s.q().trim()), "i"));
        }
        if (s.category() != null && !s.category().isBlank()) {
            filters.add(where("category").is(normalizeCategory(s.category())));
        }
        if (s.minPrice() != null || s.maxPrice() != null) {
            Criteria price = where("price");
            if (s.minPrice() != null) price = price.gte(s.minPrice());
            if (s.maxPrice() != null) price = price.lte(s.maxPrice());
            filters.add(price);
        }
        if (Boolean.TRUE.equals(s.inStock())) {
            filters.add(where("stock").gt(0));
        }
        Query query = new Query(new Criteria().andOperator(filters)).with(page);
        List<Product> content = mongo.find(query, Product.class);
        return PageableExecutionUtils.getPage(content, page,
                () -> mongo.count(Query.of(query).limit(-1).skip(-1), Product.class));
    }

    public List<Product> lowStock(int threshold) {
        return products.findByActiveTrueAndStockLessThanEqualOrderByStockAsc(threshold);
    }

    static String normalizeCategory(String c) {
        return c.trim().toLowerCase(Locale.ROOT);
    }
}
