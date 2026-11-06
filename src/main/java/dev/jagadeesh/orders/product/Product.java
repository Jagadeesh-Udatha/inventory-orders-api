package dev.jagadeesh.orders.product;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document("products")
@CompoundIndex(name = "category_price", def = "{'category': 1, 'price': 1}")
public class Product {

    @Id
    private String id;
    @Indexed(unique = true)
    private String sku;
    private String name;
    private String category;
    private BigDecimal price;
    /** Only ever changed with a conditional $inc (see StockService), never read-modify-write. */
    private int stock;
    private boolean active = true;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
    @Version
    private Long version;

    protected Product() {
    }

    public Product(String sku, String name, String category, BigDecimal price, int stock) {
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.price = price;
        this.stock = stock;
    }

    void updateDetails(String name, String category, BigDecimal price, boolean active) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.active = active;
    }

    public String getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public int getStock() { return stock; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
