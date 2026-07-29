# Axiom Strike: JDBC

A demo showing how to interact with **Axiom** over JDBC — no Spring, no ORM,
no reflection. It boots a plain `com.sun.net.httpserver.HttpServer`, wires a
few endpoints to `AxiomWarp`, and lets you hit them with `curl` to see reads,
bulk writes, and parallel queries go through the JDBC engine.

---

## 🏛️ Integration

Summon the Specification engine into your project:

**Maven**

```xml 
<dependency>    
     <groupId>com.ensemblu</groupId>   
     <artifactId>axiom-strike-jdbc</artifactId>   
     <version>1.0.0</version>  
</dependency>   
```   
**Gradle**

```groovy
implementation("com.ensemblu:axiom-strike-jdbc:1.0.0")   
```

---

## What it demonstrates

| Endpoint | Axiom feature |
|---|---|
| `POST /strike/accounts/query` | A single parameterized read (`warp.read` + `.dynamic(...)`) |
| `POST /strike/accounts/bulk-txn` | Batched inserts (`warp.write` + `.bulk(...)`) |
| `POST /strike/system/parallel-metrics` | Concurrent reads fired together (`warp.parallel(...)`) |

Every request body is validated against a schema in `src/main/resources/schemas`
before it reaches SQL (`SchemaGuard`), and every request maps its own
`AxiomProtocol` contract by hand instead of relying on annotations or a driver
doing type inference for you.

On boot, `Seeder` drops and recreates the whole demo schema (`accounts`,
`ledger_transactions`, `account_limits`, `app_users`, `account_holders`,
`audit_logs`, an audit trigger, and a summary view), then loads
`src/main/resources/csv/initial_accounts.csv` as starting data via
`warp.ingest()`, which streams the CSV straight into a batched JDBC insert
(see `Hammer`) rather than any bulk-load tooling. So every run starts from a
clean, known state.

## Prerequisites

- Java 26
- Maven
- A running PostgreSQL instance with a database matching
  `src/main/resources/postgres-strike.properties`:

  
```properties
# ==============================================================================
# Axiom JDBC: Engine Engagement Perimeter
# ==============================================================================
# Immutable configuration contract. Direct parameter binding — zero abstraction layers.
# Ensure your PostgreSQL instance has the corresponding role and database initialized.
# ==============================================================================

engine.url=jdbc:postgresql://localhost:5432/axiom_demo?prepareThreshold=0
engine.user=axiom_commander
engine.password=strike_hard
```

  Create the role/database first (or edit the properties file to match
  whatever you already have):

```sql
  CREATE ROLE axiom_commander WITH LOGIN PASSWORD 'strike_hard';
  CREATE DATABASE axiom_demo OWNER axiom_commander;
 ```

## Running it

```bash
mvn compile exec:java
```

This starts the gateway on **port 8089**, re-seeds the database, and logs
`🚀 [AXIOM STRIKE] Gateway active on port 8089` once it's ready.

You can also build a standalone jar:

```bash
mvn package
java --enable-preview -jar target/axiom-arsenal.jar
```

## Try it

```bash
# Query accounts
curl -X POST -H "Content-Type: application/json" \
  -d '{"status": "ACTIVE", "limit": 1}' \
  http://localhost:8089/strike/accounts/query

# Insert a batch of ledger transactions
curl -X POST -H "Content-Type: application/json" \
  -d '{"transactions": [{"account_id": "11111111-1111-1111-1111-111111111111", "amount": 125.50, "direction": "CREDIT", "reference_note": "Test Deposit"}]}' \
  http://localhost:8089/strike/accounts/bulk-txn

# Run two reads in parallel
curl -X POST -H "Content-Type: application/json" \
  -d '{"status": "ACTIVE", "currency": "ILS"}' \
  http://localhost:8089/strike/system/parallel-metrics
```

## Project layout

```
src/main/java/com/ensemblu/axiom/jdbc/strike/
├── BootstrapEngine.java        entry point
├── gateway/StrikeGateway.java  wires infra + routes, starts the HTTP server
├── router/Router.java          minimal routing table (no framework)
├── infra/
│   ├── StrikeInfrastructure.java  builds the HikariCP-backed AxiomWarp
│   └── Seeder.java                drops/recreates schema, loads seed CSV
├── adapter/AxiomDataSourceAdapter.java
└── handler/
    ├── AccountQueryExecutor.java
    ├── BulkTransactionIngestor.java
    └── ParallelAnalyticsEngine.java
```

---


## 📜 Legal

This project is governed by the principles of immutable software architecture. See `LICENSE.md` for the specific terms of use.
