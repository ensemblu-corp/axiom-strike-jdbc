
# ⚔️ Axiom Strike: JDBC

![Version](https://img.shields.io/badge/version-2.0.0-blue)
![Java](https://img.shields.io/badge/Java-26-orange)
![Stack](https://img.shields.io/badge/stack-Axiom%20Warp%20JDBC-informational)
![License](https://img.shields.io/badge/license-Limited%20Commercial-red)

**Live demo of the Axiom JDBC stack — no Spring, no ORM, no reflection.**

A plain `com.sun.net.httpserver.HttpServer` wires three endpoints to `AxiomWarp`. You hit them with `curl` and see parameterized reads, bulk inserts, and parallel queries run through the real engine.

Every request body is validated with `SchemaGuard` against an `.axiom` schema. Every SQL call carries an explicit `AxiomProtocol` contract. On boot, `Seeder` rebuilds the demo schema and loads seed data via `warp.ingest()` (Hammer / CSV → batched JDBC).

---

## What it demonstrates

| Endpoint | Method | Axiom feature |
|----------|--------|----------------|
| `/strike/accounts/query` | `POST` | Single parameterized read — `warp.read` + `.dynamic(...)` |
| `/strike/accounts/bulk-txn` | `POST` | Batched inserts — `warp.write` + `.bulk(...)` |
| `/strike/system/parallel-metrics` | `POST` | Concurrent reads — `warp.parallel(...)` |

Pipeline on every request:

```text
HTTP body (bytes)
    → SchemaGuard.checkContent(bytes).basedOnSchemaInPath(...).withParser(JsonParser…)
    → PersistentMap
    → AxiomWarp strike / bulk / parallel
    → PersistentMap / PersistentList
    → JsonEmitter (response)
```

---

## Prerequisites

- **Java 26** (preview features enabled)
- **Maven**
- **PostgreSQL** with a database and role matching `src/main/resources/postgres-strike.properties`

Default properties:

```properties
engine.url=jdbc:postgresql://localhost:5432/axiom_demo?prepareThreshold=0
engine.user=axiom_commander
engine.password=strike_hard
engine.pool.min=4
engine.pool.max=12
```

Create the role and database once (or edit the properties file):

```sql
CREATE ROLE axiom_commander WITH LOGIN PASSWORD 'strike_hard';
CREATE DATABASE axiom_demo OWNER axiom_commander;
```

---

## Dependencies (Maven)

This demo is versioned **2.0.0** and depends on:

| Artifact | Role |
|----------|------|
| `com.ensemblu:axiom-warp-jdbc:2.0.0` | Blocking JDBC warp engine |
| `com.ensemblu:axiom-language:2.0.0` | `SchemaGuard` + schema handshake |
| `com.zaxxer:HikariCP` | Connection pool |
| `org.postgresql:postgresql` | JDBC driver |
| `org.slf4j:slf4j-simple` | Logging |

(Transitive: `axiom-spec`, `axiom-sovereign`, `axiom`.)

---

## Running it

```bash
mvn compile exec:java
```

Starts the gateway on **port 8089**, re-seeds the database, then logs:

```text
🚀 [AXIOM STRIKE] Gateway active on port 8089
```

Standalone jar:

```bash
mvn package
java --enable-preview -jar target/axiom-arsenal.jar
```

---

## Try it

```bash
# 1. Query accounts (status + limit)
curl -X POST -H "Content-Type: application/json" \
  -d '{"status": "ACTIVE", "limit": 1}' \
  http://localhost:8089/strike/accounts/query

# 2. Bulk-insert ledger transactions
curl -X POST -H "Content-Type: application/json" \
  -d '{"transactions": [{"account_id": "11111111-1111-1111-1111-111111111111", "amount": 125.50, "direction": "CREDIT", "reference_note": "Test Deposit"}]}' \
  http://localhost:8089/strike/accounts/bulk-txn

# 3. Parallel metrics (two reads at once)
curl -X POST -H "Content-Type: application/json" \
  -d '{"status": "ACTIVE", "currency": "ILS"}' \
  http://localhost:8089/strike/system/parallel-metrics
```

---

## How the code actually works

### Boot

```java
// BootstrapEngine
StrikeGateway.launchOnPort(8089);

// StrikeGateway
final var warp = StrikeInfrastructure.initialize();
Seeder.initializeLedgerData(warp);
// bind HttpServer + Router routes
```

### Infrastructure (Hikari → AxiomWarp)

```java
final var config = Axiom.Config.file("postgres-strike.properties");

return AxiomWarp.connect(config)
        .withPoolProvider(map -> {
            final var hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(map.targetKey("engine.url").toStringVal());
            hikariConfig.setUsername(map.targetKey("engine.user").toStringVal());
            hikariConfig.setPassword(map.targetKey("engine.password").toStringVal());
            hikariConfig.setMinimumIdle(map.targetKey("engine.pool.min").toIntVal());
            hikariConfig.setMaximumPoolSize(map.targetKey("engine.pool.max").toIntVal());
            hikariConfig.setAutoCommit(false);
            return AxiomDataSourceAdapter.of(new HikariDataSource(hikariConfig));
        })
        .validateRules()
        .map(AxiomWarp::new)
        .getOrThrow();
```

### Account query (schema → dynamic strike)

```java
final var data = SchemaGuard
        .checkContent(substance)   // substance = request body as byte[]
        .basedOnSchemaInPath("schemas/account_query_schema")
        .withParser(s -> JsonParser.take(s).openBuffer().ensureRootIsObject().parseObject())
        .getOrThrow();

return warp.read(() ->
        warp.strike()
                .dynamic("""
                        SELECT account_id, balance, currency, status
                        FROM accounts
                        WHERE status = :java.status
                        ORDER BY created_at DESC
                        LIMIT :java.limit;""")
                .withContract(deriveContractFromData(data))
                .withData(data)
                .map(l -> Axiom.Data.<String, Object>emptyMap()
                        .put("accounts", l.map(JsonEmitter::emit)))
                .getOrThrow()
);
```

### Bulk transactions

```java
final var batchData = data.targetKey("transactions").toStringKeyMapListVal();

return warp.write(() ->
        warp.strike()
                .bulk("""
                        INSERT INTO ledger_transactions (account_id, amount, direction, reference_note)
                        VALUES (:java.account_id::uuid, :java.amount::double precision, :java.direction, :java.reference_note)
                        """)
                .withContract(contract)
                .withData(batchData)
                .map(count -> Axiom.Data.<String, Object>emptyMap()
                        .put("status", "BULK_SUCCESS")
                        .put("inserted_rows", count))
);
```

### Parallel metrics

```java
final var tasks = List.of(
        StrikeInstruction.dynamic("SELECT COUNT(*) AS total_accounts FROM accounts WHERE status = :java.status")
                .withContract(...)
                .withData(...),
        StrikeInstruction.dynamic("SELECT SUM(balance) AS total_liability FROM accounts WHERE currency = :java.currency")
                .withContract(...)
                .withData(...)
);

return Axiom.Data.<String, Object>emptyMap()
        .put("parallel_metrics", warp.parallel(tasks).getOrThrow().map(JsonEmitter::emit));
```

---

## Project layout

```
src/main/java/com/ensemblu/axiom/jdbc/strike/
├── BootstrapEngine.java              // main entry
├── gateway/StrikeGateway.java        // infra + routes + HttpServer
├── router/Router.java                // minimal routing (no framework)
├── infra/
│   ├── StrikeInfrastructure.java     // HikariCP → AxiomWarp
│   └── Seeder.java                   // drop/recreate schema + CSV seed
├── adapter/AxiomDataSourceAdapter.java
└── handler/
    ├── AccountQueryExecutor.java     // /accounts/query
    ├── BulkTransactionIngestor.java  // /accounts/bulk-txn
    └── ParallelAnalyticsEngine.java  // /system/parallel-metrics

src/main/resources/
├── postgres-strike.properties
├── csv/initial_accounts.csv
├── schemas/
│   ├── account_query_schema.axiom
│   ├── bulk_transaction_schema.axiom
│   └── parallel_analytics_schema.axiom
└── example.axiom
```

---

## Seeding

On every start, `Seeder`:

1. Drops and recreates tables: `accounts`, `ledger_transactions`, `account_limits`, `app_users`, `account_holders`, `audit_logs`, plus an audit trigger and a summary view  
2. Loads `csv/initial_accounts.csv` through `warp.ingest()` (Hammer streams rows into batched inserts)

You always begin from a known, clean state.

---

## Design notes

- **No Spring / no ORM** — JDK `HttpServer` + explicit Axiom APIs only  
- **Byte-first** — request bodies stay as `byte[]` until `JsonParser` / `SchemaGuard`  
- **Contracts are hand-written** — `AxiomProtocol` maps are built in code, not inferred  
- **JSON out via `JsonEmitter`** — not `Dop.toJson` (removed in core 2.0.0)  
- **Pool does not own transactions** — `autoCommit(false)`; Axiom scopes commit/rollback  

---

## Related modules

| Module | Role in this demo |
|--------|-------------------|
| [`axiom-warp-jdbc`](https://github.com/ensemblu-corp/axiom-warp-jdbc) | `AxiomWarp`, strikes, ingest, parallel |
| [`axiom-language`](https://github.com/ensemblu-corp/axiom-language) | `SchemaGuard` |
| [`axiom-spec`](https://github.com/ensemblu-corp/axiom-spec) | `JsonParser`, `JsonEmitter`, `AxiomProtocol`, `StrikeInstruction` |
| [`axiom`](https://github.com/ensemblu-corp/axiom) | `PersistentMap`, `Result`, `Dop`, `Axiom` entry point |

---

## Legal

Limited Commercial License — free for evaluation, testing, and non-commercial development.  
Commercial or production use requires a paid annual contract from Ensemblu Corp.

See `LICENSE.md`. Contact: **contact@ensemblu.com**
