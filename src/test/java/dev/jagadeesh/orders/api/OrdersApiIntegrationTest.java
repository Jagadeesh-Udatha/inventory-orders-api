package dev.jagadeesh.orders.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jagadeesh.orders.order.Order;
import dev.jagadeesh.orders.product.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full HTTP flows through MockMvc against a real (embedded) MongoDB. */
@SpringBootTest
@AutoConfigureMockMvc
class OrdersApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired MongoTemplate mongo;
    @Autowired ObjectMapper json;

    @BeforeEach
    void seed() throws Exception {
        mongo.remove(new org.springframework.data.mongodb.core.query.Query(), Order.class);
        mongo.remove(new org.springframework.data.mongodb.core.query.Query(), Product.class);
        product("KB-MECH-87", "Mechanical keyboard", "Electronics", "3499.00", 10);
        product("MS-WL-01", "Wireless mouse", "electronics", "899.50", 3);
        product("BK-DDIA", "Designing Data-Intensive Applications", "books", "1299.00", 5);
    }

    @Test
    void placingAnOrderReservesStockAndSnapshotsPrices() throws Exception {
        JsonNode order = place("cust-1", null, "KB-MECH-87", 2, "MS-WL-01", 1).andExpect(status().isCreated()).andReturnJson();
        assertThat(order.get("status").asText()).isEqualTo("PLACED");
        assertThat(order.get("total").decimalValue()).isEqualByComparingTo("7897.50");
        assertThat(order.get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("3499.00");
        assertThat(stock("KB-MECH-87")).isEqualTo(8);
        assertThat(stock("MS-WL-01")).isEqualTo(2);
    }

    @Test
    void notEnoughStockFailsWholeOrderAndReleasesEarlierLines() throws Exception {
        place("cust-1", null, "KB-MECH-87", 4, "MS-WL-01", 5)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertThat(stock("KB-MECH-87")).as("first line was given back").isEqualTo(10);
        assertThat(stock("MS-WL-01")).isEqualTo(3);
        assertThat(mongo.count(new org.springframework.data.mongodb.core.query.Query(), Order.class)).isZero();
    }

    @Test
    void concurrentBuyersNeverOversell() throws Exception {
        int buyers = 40;   // 40 people, 10 keyboards
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            String customer = "cust-" + i;
            calls.add(() -> place(customer, null, "KB-MECH-87", 1).andReturn().getResponse().getStatus());
        }
        int created = 0, conflicts = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (Future<Integer> f : pool.invokeAll(calls)) {
                if (f.get() == 201) created++;
                else if (f.get() == 409) conflicts++;
            }
        }
        assertThat(created).isEqualTo(10);
        assertThat(conflicts).isEqualTo(30);
        assertThat(stock("KB-MECH-87")).isZero();
    }

    @Test
    void sameIdempotencyKeyReturnsTheFirstOrderAndReservesOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = place("cust-1", key, "BK-DDIA", 2).andExpect(status().isCreated()).andReturnJson().get("id").asText();
        String retry = place("cust-1", key, "BK-DDIA", 2).andExpect(status().isOk()).andReturnJson().get("id").asText();
        assertThat(retry).isEqualTo(first);
        assertThat(stock("BK-DDIA")).isEqualTo(3);
    }

    @Test
    void cancellingReturnsStockAndIllegalTransitionsAreRejected() throws Exception {
        String id = place("cust-1", null, "KB-MECH-87", 3).andReturnJson().get("id").asText();
        assertThat(stock("KB-MECH-87")).isEqualTo(7);

        mvc.perform(post("/api/orders/" + id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.history.length()").value(2));
        assertThat(stock("KB-MECH-87")).isEqualTo(10);

        mvc.perform(post("/api/orders/" + id + "/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_TRANSITION"));
        assertThat(stock("KB-MECH-87")).as("no double restock").isEqualTo(10);
    }

    @Test
    void shippedOrdersCannotBeCancelled() throws Exception {
        String id = place("cust-1", null, "MS-WL-01", 1).andReturnJson().get("id").asText();
        moveTo(id, "CONFIRMED").andExpect(status().isOk());
        moveTo(id, "SHIPPED").andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + id + "/cancel")).andExpect(status().isConflict());
        moveTo(id, "DELIVERED").andExpect(jsonPath("$.status").value("DELIVERED"));
        assertThat(stock("MS-WL-01")).isEqualTo(2);
    }

    @Test
    void searchFiltersPagesAndSorts() throws Exception {
        mvc.perform(get("/api/products").param("category", "ELECTRONICS").param("sort", "price").param("dir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].sku").value("KB-MECH-87"));
        mvc.perform(get("/api/products").param("maxPrice", "1000"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("MS-WL-01"));
        mvc.perform(get("/api/products").param("q", "data-intensive"))
                .andExpect(jsonPath("$.content[0].sku").value("BK-DDIA"));
        mvc.perform(get("/api/products").param("q", ".*"))
                .andExpect(jsonPath("$.totalElements").value(0));     // regex characters are matched literally
        mvc.perform(get("/api/products").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/products").param("sort", "version")).andExpect(status().isBadRequest());
    }

    @Test
    void salesReportGroupsByCategoryAndSkipsCancelledOrders() throws Exception {
        place("cust-1", null, "KB-MECH-87", 2, "BK-DDIA", 1);
        place("cust-2", null, "MS-WL-01", 2);
        String cancelled = place("cust-3", null, "KB-MECH-87", 5).andReturnJson().get("id").asText();
        mvc.perform(post("/api/orders/" + cancelled + "/cancel"));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        mvc.perform(get("/api/reports/sales-by-category")
                        .param("from", today.toString()).param("to", today.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("electronics"))
                .andExpect(jsonPath("$[0].unitsSold").value(4))
                .andExpect(jsonPath("$[0].revenue").value(8797.0))
                .andExpect(jsonPath("$[0].orders").value(2))
                .andExpect(jsonPath("$[1].category").value("books"))
                .andExpect(jsonPath("$[1].revenue").value(1299.0));
    }

    @Test
    void lowStockAndRestock() throws Exception {
        mvc.perform(get("/api/reports/low-stock").param("threshold", "5"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sku").value("MS-WL-01"));
        mvc.perform(post("/api/products/MS-WL-01/restock").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(23));
        mvc.perform(post("/api/products/MS-WL-01/restock").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validationDuplicatesAndUnknowns() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"bad sku\",\"name\":\"\",\"category\":\"x\",\"price\":-1,\"stock\":-2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sku").exists())
                .andExpect(jsonPath("$.errors.price").exists())
                .andExpect(jsonPath("$.errors.stock").exists());
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content(body("KB-MECH-87", "Copy", "electronics", "1.00", 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SKU"));
        place("cust-1", null, "NOPE-1", 1).andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/does-not-exist")).andExpect(status().isNotFound());
        mvc.perform(put("/api/products/KB-MECH-87").contentType(MediaType.APPLICATION_JSON)
                        .content(body("KB-MECH-87", "Mechanical keyboard v2", "electronics", "3299.00", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(3299.0))
                .andExpect(jsonPath("$.stock").value(10));   // PUT never changes stock
    }

    // ---------------------------------------------------------------- helpers

    private void product(String sku, String name, String category, String price, int stock) throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body(sku, name, category, price, stock)))
                .andExpect(status().isCreated());
    }

    private static String body(String sku, String name, String category, String price, int stock) {
        return String.format("{\"sku\":\"%s\",\"name\":\"%s\",\"category\":\"%s\",\"price\":%s,\"stock\":%d}",
                sku, name, category, price, stock);
    }

    private JsonResult place(String customer, String key, Object... skuQty) throws Exception {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < skuQty.length; i += 2) {
            if (i > 0) items.append(',');
            items.append(String.format("{\"sku\":\"%s\",\"quantity\":%d}", skuQty[i], skuQty[i + 1]));
        }
        var req = post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"" + customer + "\",\"items\":[" + items + "]}");
        if (key != null) req.header("Idempotency-Key", key);
        return new JsonResult(mvc.perform(req));
    }

    private ResultActions moveTo(String id, String target) throws Exception {
        return mvc.perform(post("/api/orders/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + target + "\"}"));
    }

    private int stock(String sku) throws Exception {
        String res = mvc.perform(get("/api/products/" + sku)).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("stock").asInt();
    }

    private final class JsonResult {
        private final ResultActions actions;

        JsonResult(ResultActions actions) { this.actions = actions; }

        JsonResult andExpect(org.springframework.test.web.servlet.ResultMatcher m) throws Exception {
            actions.andExpect(m);
            return this;
        }

        org.springframework.test.web.servlet.MvcResult andReturn() { return actions.andReturn(); }

        JsonNode andReturnJson() throws Exception {
            return json.readTree(actions.andReturn().getResponse().getContentAsString());
        }
    }
}
