package dev.jagadeesh.orders.api;

import dev.jagadeesh.orders.product.Product;
import dev.jagadeesh.orders.product.ProductRequest;
import dev.jagadeesh.orders.product.ProductSearch;
import dev.jagadeesh.orders.product.ProductService;
import dev.jagadeesh.orders.product.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Set;

@Tag(name = "Products", description = "Catalogue and stock")
@Validated
@RestController
@RequestMapping("/api/products")
public class ProductController {

    /** Only these fields can be sorted on, so callers can't sort on unindexed or internal fields. */
    private static final Set<String> SORTABLE = Set.of("name", "price", "stock", "createdAt");

    private final ProductService products;
    private final StockService stock;

    public ProductController(ProductService products, StockService stock) {
        this.products = products;
        this.stock = stock;
    }

    @Operation(summary = "Add a product")
    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody ProductRequest req) {
        Product p = products.create(req);
        return ResponseEntity.created(URI.create("/api/products/" + p.getSku())).body(p);
    }

    @Operation(summary = "Search active products with filters, paging and sorting")
    @GetMapping
    public PageResponse<Product> search(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) BigDecimal minPrice,
                                        @RequestParam(required = false) BigDecimal maxPrice,
                                        @RequestParam(required = false) Boolean inStock,
                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                        @RequestParam(defaultValue = "name") String sort,
                                        @RequestParam(defaultValue = "asc") String dir) {
        if (!SORTABLE.contains(sort)) {
            throw new IllegalArgumentException("sort must be one of " + SORTABLE);
        }
        Sort order = Sort.by("desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC, sort);
        return PageResponse.of(products.search(new ProductSearch(q, category, minPrice, maxPrice, inStock),
                PageRequest.of(page, size, order)));
    }

    @Operation(summary = "Get a product by SKU")
    @GetMapping("/{sku}")
    public Product get(@PathVariable String sku) {
        return products.get(sku);
    }

    @Operation(summary = "Update name, category, price or active flag")
    @PutMapping("/{sku}")
    public Product update(@PathVariable String sku, @Valid @RequestBody ProductRequest req) {
        return products.update(sku, req);
    }

    @Operation(summary = "Add units to stock")
    @PostMapping("/{sku}/restock")
    public Product restock(@PathVariable String sku, @Valid @RequestBody RestockRequest body) {
        return stock.restock(sku, body.quantity());
    }

    public record RestockRequest(@Positive @Max(100_000) int quantity) {
    }
}
