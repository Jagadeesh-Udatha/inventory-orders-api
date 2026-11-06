package dev.jagadeesh.orders.api;

import dev.jagadeesh.orders.product.Product;
import dev.jagadeesh.orders.product.ProductService;
import dev.jagadeesh.orders.report.CategorySales;
import dev.jagadeesh.orders.report.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Tag(name = "Reports")
@Validated
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reports;
    private final ProductService products;
    private final int defaultThreshold;

    public ReportController(ReportService reports, ProductService products,
                            @Value("${inventory.low-stock-threshold}") int defaultThreshold) {
        this.reports = reports;
        this.products = products;
        this.defaultThreshold = defaultThreshold;
    }

    @Operation(summary = "Units and revenue per category between two dates (UTC, to is exclusive)")
    @GetMapping("/sales-by-category")
    public List<CategorySales> salesByCategory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("'to' must be after 'from'");
        }
        return reports.salesByCategory(from.atStartOfDay().toInstant(ZoneOffset.UTC), to.atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    @Operation(summary = "Active products at or below a stock threshold, lowest first")
    @GetMapping("/low-stock")
    public List<Product> lowStock(@RequestParam(required = false) @Min(0) Integer threshold) {
        return products.lowStock(threshold == null ? defaultThreshold : threshold);
    }
}
