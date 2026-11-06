package dev.jagadeesh.orders.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PlaceOrderRequest(
        @Schema(example = "cust-1042") @NotBlank @Size(max = 64) String customerId,
        @NotEmpty @Size(max = 50) List<@Valid Line> items) {

    public record Line(@Schema(example = "KB-MECH-87") @NotBlank String sku,
                       @Schema(example = "2") @Positive @Max(100) int quantity) {
    }
}
