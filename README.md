# Inventory & Orders API

An e-commerce **order and inventory service** built with **Spring Boot 3** and **MongoDB**. It never oversells, makes order retries safe with idempotency keys, enforces an order lifecycle, and reports sales with aggregation pipelines.

Java 21 · Spring Boot 3.4 · Spring Data MongoDB · Bean Validation · springdoc OpenAPI · JUnit 5 · Embedded MongoDB · Docker · GitHub Actions

## API

| Method | Path | What it does |
|---|---|---|
| `POST` | `/api/products` | Add a product (`sku`, `name`, `category`, `price`, `stock`) |
| `GET` | `/api/products?q=&category=&minPrice=&maxPrice=&inStock=&page=&size=&sort=&dir=` | Search with filters, paging and sorting |
| `GET` / `PUT` | `/api/products/{sku}` | Read or update catalogue details (stock is never changed by `PUT`) |
| `POST` | `/api/products/{sku}/restock` | Add units (`{"quantity": 20}`) |
| `POST` | `/api/orders` | Place an order; optional `Idempotency-Key` header |
| `GET` | `/api/orders/{id}` · `/api/orders?customerId=&status=` | One order · a customer's orders, newest first |
| `POST` | `/api/orders/{id}/status` · `/api/orders/{id}/cancel` | Move through the lifecycle · cancel and restock |
| `GET` | `/api/reports/sales-by-category?from=2026-11-01&to=2026-12-01` | Units, revenue and order count per category |
| `GET` | `/api/reports/low-stock?threshold=5` | Products running out |

Swagger UI: `http://localhost:8080/swagger-ui.html` · Health: `/actuator/health`

All errors are RFC 7807 `application/problem+json` with a stable `code` (`VALIDATION_FAILED`, `NOT_FOUND`, `INSUFFICIENT_STOCK`, `DUPLICATE_SKU`, `ILLEGAL_TRANSITION`, `CONCURRENT_UPDATE`).

## Design

### No overselling, without locks or transactions

Each order line is reserved with one conditional, atomic update:

```js
db.products.findOneAndUpdate(
  { sku: "KB-MECH-87", active: true, stock: { $gte: 2 } },   // only if enough is left
  { $inc: { stock: -2, version: 1 } })
```

Two buyers can never both take the last unit, because the check and the decrement are one operation on one document. A test sends **40 concurrent orders for 10 keyboards** and expects exactly 10 `201`s, 30 `409`s and a stock of 0.

If a later line of the same order fails (say, the mouse is out of stock), the lines already reserved are **released** before the `409` is returned. This compensating step keeps stock correct without multi-document transactions, which would need a MongoDB replica set.

### Safe retries with `Idempotency-Key`

A client that times out can resend the same request with the same key. The key has a **unique sparse index** on `orders`, so the retry returns the original order (`200`) and stock is reserved only once. If two retries race, the loser hits the unique index, gives its stock back and returns the winner's order.

### Order lifecycle

```
PLACED ──▶ CONFIRMED ──▶ SHIPPED ──▶ DELIVERED
   │            │
   └──────┬─────┘
          ▼
      CANCELLED   (stock goes back on the shelf; not allowed once shipped)
```

Allowed transitions live in the `OrderStatus` enum; anything else is `409 ILLEGAL_TRANSITION`. Every change is appended to the order's `history`. `@Version` (optimistic locking) turns two simultaneous status changes into one success and one `409 CONCURRENT_UPDATE` instead of a lost update, so a cancelled order can't be restocked twice.

### Data model

| Collection | Notes |
|---|---|
| `products` | Unique index on `sku`; compound index `(category, price)` for the filtered search; money stored as `Decimal128` (exact, and summable in aggregations) |
| `orders` | Line items are **snapshots** (name, category, unit price at order time), so later price changes don't rewrite history; indexes on `(customerId, createdAt)`, `status`, and unique sparse `idempotencyKey` |

### Reporting

`sales-by-category` is one aggregation pipeline: `$match` (date window, not cancelled) → `$unwind` items → `$group` by category (`$sum` units and revenue, distinct orders) → `$sort` by revenue.

### Other details

- Search text is escaped with `Pattern.quote`, so user input is matched literally and never runs as a regex.
- Sorting is restricted to a whitelist of fields.
- Pages are returned in a stable `PageResponse` shape rather than Spring's internal `PageImpl`.
- `createdAt` and `updatedAt` come from Spring Data auditing; time is injected as a `Clock`.

## Run it

```bash
docker compose up --build

curl -s -X POST localhost:8080/api/products -H 'Content-Type: application/json' \
  -d '{"sku":"KB-MECH-87","name":"Mechanical keyboard","category":"electronics","price":3499.00,"stock":10}'

curl -s -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"customerId":"cust-1042","items":[{"sku":"KB-MECH-87","quantity":2}]}'
```

Without Docker: start MongoDB on `localhost:27017`, then `mvn spring-boot:run`. Settings: `MONGO_HOST`, `MONGO_PORT`, `MONGO_DB` (default `orders`).

## Tests

```bash
mvn verify
```

21 tests. The integration suite drives the HTTP API through MockMvc against a real embedded MongoDB (no Docker needed). It covers ordering and price snapshots, all-or-nothing stock reservation, 40 concurrent buyers, idempotent retries, cancel/restock and illegal transitions, shipped orders, search filters, paging, sorting and regex safety, the sales report, low stock and restock, and validation and duplicate errors. The unit tests cover every lifecycle transition.
