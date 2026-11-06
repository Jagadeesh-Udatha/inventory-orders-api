package dev.jagadeesh.orders.product;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @Schema(example = "KB-MECH-87") @NotBlank @Pattern(regexp = "^[A-Z0-9-]{3,32}$", message = "must be 3-32 upper-case letters, digits or '-'") String sku,
        @Schema(example = "Mechanical keyboard, 87 keys") @NotBlank @Size(max = 120) String name,
        @Schema(example = "electronics") @NotBlank @Size(max = 40) String category,
        @Schema(example = "3499.00") @NotNull @DecimalMin("0.01") @Digits(integer = 9, fraction = 2) BigDecimal price,
        @Schema(example = "25") @PositiveOrZero int stock,
        Boolean active) {
}
