# Oracle Database Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Oracle 19c as a second supported database vendor alongside MariaDB, selectable at build time, per the approved spec at `docs/superpowers/specs/2026-06-10-oracle-support-design.md`.

**Architecture:** Repositories are rewritten in portable ANSI SQL that runs on both MariaDB 10.11 and Oracle 19c (no dialect abstraction). Only Flyway DDL is vendor-specific (`db/migration/mariadb/` vs `db/migration/oracle/`). Vendor selection happens at build time via `-Ddb=oracle`, which activates a Maven profile that swaps the database dependencies and (via resource filtering) the datasource config. Integration tests run against either vendor via a single `DatabaseTestResource` that picks the Testcontainers image from the `db` system property.

**Tech Stack:** Quarkus 3.24 / Kotlin 2.2, Vert.x reactive SQL clients (`quarkus-reactive-mysql-client` / `quarkus-reactive-oracle-client`), Flyway, Testcontainers (MariaDB + `gvenzl/oracle-free`).

**Conventions for this plan:**
- All commands run from the repo root `/home/brandy/projects/file-exchange-hub`.
- "Run ITs" means `./mvnw verify -DskipITs=false` (requires Docker).
- Run `./mvnw ktlint:format` before every commit that touches Kotlin.
- Note: there is no free 19c container image; Oracle ITs run against `gvenzl/oracle-free` (23ai). The SQL we write is 19c-compatible by construction (only `OFFSET/FETCH`, `DUAL`, `VARCHAR2`, `TIMESTAMP`, `CHECK` — all 12c+ features).

---

### Task 1: Baseline IT coverage for search pagination

The `search()` repository method (the third place with vendor-specific SQL) has no integration test. Add one before touching any SQL so Task 2's rewrite has a safety net.

**Files:**
- Modify: `src/test/kotlin/mlid/enghub/hub/resource/FileResourceIT.kt`

- [ ] **Step 1: Add the search IT**

Add this test method inside the `FileResourceIT` class (after the `missing returns 200 with files and total` test):

```kotlin
    @Test
    fun `search paginates registered files`() {
        // Register two files with a distinct uploader so this test is isolated
        listOf("search/one.pdf", "search/two.pdf").forEach { key ->
            val body =
                validBody
                    .replace("test/sample.pdf", key)
                    .replace("test-client", "search-client")
            Given {
                contentType("application/json")
                body(body)
            } When {
                post("/api/files/register")
            } Then {
                statusCode(201)
            }
        }

        When {
            get("/api/files?uploaderId=search-client&page=0&size=1")
        } Then {
            statusCode(200)
            body("files.size()", equalTo(1))
            body("total", equalTo(2))
        }

        When {
            get("/api/files?uploaderId=search-client&page=1&size=1")
        } Then {
            statusCode(200)
            body("files.size()", equalTo(1))
        }
    }
```

Note: `MinioTestResource` must contain objects at the registered keys for registration to succeed. Check `src/test/kotlin/mlid/enghub/hub/MinioTestResource.kt` — the existing tests register `test/sample.pdf`, so the resource either pre-creates that object or creates the bucket and objects. If it pre-creates only `test/sample.pdf`, add `search/one.pdf` and `search/two.pdf` to the same setup code (same content is fine).

- [ ] **Step 2: Run the new IT, verify it passes on MariaDB**

Run: `./mvnw verify -DskipITs=false -Dit.test=FileResourceIT`
Expected: PASS (all 7 tests). This is a baseline test — it must pass before the SQL rewrite.

- [ ] **Step 3: Commit**

```bash
./mvnw ktlint:format
git add src/test/kotlin
git commit -m "test: add search pagination integration test"
```

### Task 2: Portable SQL rewrites in repositories

Replace the three MySQL-only constructs with ANSI SQL that runs on both MariaDB 10.11 and Oracle 19c. Behavior must not change; the ITs from Task 1 prove it.

**Files:**
- Modify: `src/main/kotlin/mlid/enghub/hub/repository/FileMetadataRepository.kt`
- Modify: `src/main/kotlin/mlid/enghub/hub/repository/FileDeliveryRepository.kt`

- [ ] **Step 1: Rewrite pagination in `FileMetadataRepository.search()`**

In `search()`, the rows query currently reads:

```kotlin
        val rows =
            pool
                .preparedQuery(
                    "SELECT * FROM file_metadata WHERE $where ORDER BY registered_at DESC LIMIT ? OFFSET ?",
                ).execute(Tuple.from(params + listOf(size, offset)))
                .awaitSuspending()
```

Replace with (note the swapped parameter order — offset binds first now):

```kotlin
        val rows =
            pool
                .preparedQuery(
                    "SELECT * FROM file_metadata WHERE $where " +
                        "ORDER BY registered_at DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                ).execute(Tuple.from(params + listOf(offset, size)))
                .awaitSuspending()
```

- [ ] **Step 2: Rewrite pagination in `FileMetadataRepository.findMissing()`**

The rows query currently reads:

```kotlin
        val rows =
            pool
                .preparedQuery("SELECT fm.* $baseSql ORDER BY fm.registered_at LIMIT ? OFFSET ?")
                .execute(Tuple.from(params + listOf(size, offset)))
                .awaitSuspending()
```

Replace with:

```kotlin
        val rows =
            pool
                .preparedQuery("SELECT fm.* $baseSql ORDER BY fm.registered_at OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")
                .execute(Tuple.from(params + listOf(offset, size)))
                .awaitSuspending()
```

- [ ] **Step 3: Rewrite `FileDeliveryRepository.insertIgnore()`**

Replace the whole method body:

```kotlin
    suspend fun insertIgnore(delivery: FileDelivery) {
        pool
            .preparedQuery(
                """
                INSERT INTO file_delivery (id, file_id, consumer_id, note, processed_at)
                SELECT ?, ?, ?, ?, ? FROM DUAL
                WHERE NOT EXISTS (
                    SELECT 1 FROM file_delivery WHERE file_id = ? AND consumer_id = ?
                )
                """.trimIndent(),
            ).execute(
                Tuple.of(
                    delivery.id,
                    delivery.fileId,
                    delivery.consumerId,
                    delivery.note,
                    delivery.processedAt.atOffset(ZoneOffset.UTC).toLocalDateTime(),
                    delivery.fileId,
                    delivery.consumerId,
                ),
            ).map { Unit }
            .awaitSuspending()
    }
```

Semantics match `INSERT IGNORE` for the duplicate case (zero rows inserted, no error). The `uq_delivery` unique constraint stays as a backstop against concurrent inserts — a race would surface as a constraint violation, same as before this change.

- [ ] **Step 4: Run all ITs, verify nothing regressed on MariaDB**

Run: `./mvnw verify -DskipITs=false`
Expected: PASS. The `mark processed is idempotent` test exercises the new `insertIgnore`; the `search paginates registered files` and `missing returns 200` tests exercise the new pagination.

- [ ] **Step 5: Commit**

```bash
./mvnw ktlint:format
git add src/main/kotlin
git commit -m "refactor: rewrite repository SQL in portable ANSI form for Oracle compat"
```

### Task 3: Vendor-specific Flyway migration directories

**Files:**
- Move: `src/main/resources/db/migration/V1__create_file_tables.sql` → `src/main/resources/db/migration/mariadb/V1__create_file_tables.sql` (content unchanged)
- Create: `src/main/resources/db/migration/oracle/V1__create_file_tables.sql`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Move the existing migration**

```bash
mkdir -p src/main/resources/db/migration/mariadb
git mv src/main/resources/db/migration/V1__create_file_tables.sql src/main/resources/db/migration/mariadb/
```

- [ ] **Step 2: Write the Oracle migration**

Create `src/main/resources/db/migration/oracle/V1__create_file_tables.sql`:

```sql
CREATE TABLE file_metadata (
    id            VARCHAR2(36)   NOT NULL PRIMARY KEY,
    bucket        VARCHAR2(255)  NOT NULL,
    report_id     VARCHAR2(255)  NOT NULL,
    report_category VARCHAR2(255) NOT NULL,
    object_key    VARCHAR2(2000) NOT NULL,
    filename      VARCHAR2(255)  NOT NULL,
    content_type  VARCHAR2(128)  NOT NULL,
    file_size     NUMBER(19)     NOT NULL,
    checksum      VARCHAR2(256),
    uploader_id   VARCHAR2(255)  NOT NULL,
    tags          VARCHAR2(4000) CHECK (tags IS JSON),
    status        VARCHAR2(10) DEFAULT 'REGISTERED' NOT NULL
                  CHECK (status IN ('REGISTERED','FAILED')),
    remark        VARCHAR2(1024),
    error_code    VARCHAR2(64),
    registered_at TIMESTAMP(6)   NOT NULL
);

CREATE INDEX idx_file_metadata_query
    ON file_metadata (registered_at, bucket, status);

CREATE TABLE file_delivery (
    id           VARCHAR2(36)  NOT NULL,
    file_id      VARCHAR2(36)  NOT NULL,
    consumer_id  VARCHAR2(255) NOT NULL,
    note         CLOB,
    processed_at TIMESTAMP(6)  NOT NULL,
    CONSTRAINT pk_file_delivery PRIMARY KEY (id),
    CONSTRAINT fk_delivery_file FOREIGN KEY (file_id) REFERENCES file_metadata(id),
    CONSTRAINT uq_delivery UNIQUE (file_id, consumer_id)
);

CREATE INDEX idx_file_delivery_file_id ON file_delivery (file_id);
```

Oracle notes baked into this script: `DEFAULT` must precede `NOT NULL`; a `NULL` value passes the `IS JSON` check (CHECK constraints pass on UNKNOWN); `ENUM` becomes `VARCHAR2` + `CHECK`.

- [ ] **Step 3: Point Flyway at the MariaDB directory (hardcoded for now)**

In `src/main/resources/application.properties`, after `quarkus.flyway.migrate-at-start=true`, add:

```properties
quarkus.flyway.locations=db/migration/mariadb
```

(Task 4 replaces the hardcoded value with a filtered property.)

- [ ] **Step 4: Run ITs, verify migrations still apply**

Run: `./mvnw verify -DskipITs=false -Dit.test=FileResourceIT`
Expected: PASS. If Flyway complains about no migrations found, the `locations` value or directory name is wrong.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources
git commit -m "feat: split Flyway migrations into per-vendor directories, add Oracle DDL"
```

### Task 4: Maven profiles and filtered datasource config

Vendor selection: `-Ddb=oracle` activates the `oracle` Maven profile; no flag (or `-Ddb=mariadb`... see activation note in Step 2) selects MariaDB. Maven resource filtering (with `@` delimiters only, so Quarkus `${ENV:default}` placeholders are untouched) injects `db-kind`, default URLs, and Flyway locations into `application.properties`.

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Move vendor-specific dependencies into profiles**

In `pom.xml`, delete these four lines from the main `<dependencies>` block:

```xml
    <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-reactive-mysql-client</artifactId></dependency>
    <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-jdbc-mariadb</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-mysql</artifactId></dependency>
```

and

```xml
    <dependency><groupId>org.testcontainers</groupId><artifactId>mariadb</artifactId><scope>test</scope></dependency>
```

Keep `quarkus-flyway` in the main block. Then add BOTH testcontainers modules to the main test dependencies (they must always compile so `DatabaseTestResource` in Task 5 builds under either profile — they're test-scope only, so this doesn't affect the runtime artifact):

```xml
    <dependency><groupId>org.testcontainers</groupId><artifactId>mariadb</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>oracle-free</artifactId><scope>test</scope></dependency>
```

- [ ] **Step 2: Add the vendor profiles**

In the `<profiles>` section (alongside the existing `native` profile), add:

```xml
    <profile>
      <id>mariadb</id>
      <activation><property><name>db</name><value>!oracle</value></property></activation>
      <properties>
        <db.kind>mariadb</db.kind>
        <db.jdbc.url>jdbc:mariadb://localhost:3306/filehub</db.jdbc.url>
        <db.reactive.url>mariadb://localhost:3306/filehub</db.reactive.url>
        <db.flyway.locations>db/migration/mariadb</db.flyway.locations>
      </properties>
      <dependencies>
        <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-reactive-mysql-client</artifactId></dependency>
        <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-jdbc-mariadb</artifactId></dependency>
        <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-mysql</artifactId></dependency>
      </dependencies>
    </profile>
    <profile>
      <id>oracle</id>
      <activation><property><name>db</name><value>oracle</value></property></activation>
      <properties>
        <db.kind>oracle</db.kind>
        <db.jdbc.url>jdbc:oracle:thin:@localhost:1521/FREEPDB1</db.jdbc.url>
        <db.reactive.url>oracle:thin:@localhost:1521/FREEPDB1</db.reactive.url>
        <db.flyway.locations>db/migration/oracle</db.flyway.locations>
      </properties>
      <dependencies>
        <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-reactive-oracle-client</artifactId></dependency>
        <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-jdbc-oracle</artifactId></dependency>
        <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-oracle</artifactId></dependency>
      </dependencies>
    </profile>
```

Activation semantics (verify in Step 6): `<value>!oracle</value>` activates `mariadb` whenever the `db` system property is absent OR not equal to `oracle`, so plain `./mvnw verify` and `-Pnative` builds keep working. `-Ddb=oracle` flips both profiles at once.

Version note: `flyway-database-oracle` should be version-managed by the Quarkus BOM. If Step 6's build fails with "missing version", find the managed Flyway version with `./mvnw help:evaluate -Dexpression=flyway.version -q -DforceStdout` (or check `./mvnw dependency:tree -Ddb=oracle | grep flyway`) and pin `flyway-database-oracle` to the same version as `flyway-core`.

- [ ] **Step 3: Enable resource filtering with `@` delimiters**

In `pom.xml` inside `<build>` (before `<plugins>`), add:

```xml
    <resources>
      <resource>
        <directory>src/main/resources</directory>
        <filtering>true</filtering>
      </resource>
    </resources>
```

And add the resources plugin to `<plugins>` so only `@...@` placeholders are filtered (Quarkus `${DB_URL:...}` placeholders must survive untouched):

```xml
      <plugin>
        <artifactId>maven-resources-plugin</artifactId>
        <configuration>
          <delimiters><delimiter>@</delimiter></delimiters>
          <useDefaultDelimiters>false</useDefaultDelimiters>
        </configuration>
      </plugin>
```

- [ ] **Step 4: Parameterize `application.properties`**

Replace the datasource block in `src/main/resources/application.properties`:

```properties
# DataSource — JDBC used only by Flyway
quarkus.datasource.db-kind=@db.kind@
quarkus.datasource.username=${DB_USER:hub}
quarkus.datasource.password=${DB_PASSWORD:hub}
quarkus.datasource.jdbc.url=${DB_URL:@db.jdbc.url@}
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=@db.flyway.locations@

# Reactive datasource — used by repositories
quarkus.datasource.reactive.url=${DB_REACTIVE_URL:@db.reactive.url@}
```

(This replaces the hardcoded `quarkus.flyway.locations=db/migration/mariadb` from Task 3.)

- [ ] **Step 5: Pass the `db` property to the failsafe JVM**

Forked test JVMs don't inherit Maven `-D` flags automatically. In `pom.xml`, add a default to `<properties>`:

```xml
    <db>mariadb</db>
```

(CLI `-Ddb=oracle` overrides this for property resolution; profile activation only looks at the CLI flag, so the POM default doesn't interfere.) Then add to BOTH the failsafe and surefire `<systemPropertyVariables>` blocks:

```xml
                <db>${db}</db>
```

- [ ] **Step 6: Verify the MariaDB path still works end to end**

Run: `./mvnw verify -DskipITs=false`
Expected: PASS. Also confirm filtering worked: `grep db-kind target/classes/application.properties` must show `quarkus.datasource.db-kind=mariadb` (not `@db.kind@`).

- [ ] **Step 7: Verify the Oracle path compiles and resolves**

Run: `./mvnw compile -Ddb=oracle`
Expected: BUILD SUCCESS (this proves dependency resolution, including `flyway-database-oracle` — apply the version-pin fallback from Step 2 if it fails). Then `grep db-kind target/classes/application.properties` must show `quarkus.datasource.db-kind=oracle`.

- [ ] **Step 8: Commit**

```bash
git add pom.xml src/main/resources/application.properties
git commit -m "build: add mariadb/oracle Maven profiles with filtered datasource config"
```

### Task 5: DatabaseTestResource for vendor-switchable ITs

Replace `MariaDbTestResource` with a single `DatabaseTestResource` that starts the right container based on the `db` system property (wired through failsafe in Task 4).

**Files:**
- Delete: `src/test/kotlin/mlid/enghub/hub/MariaDbTestResource.kt`
- Create: `src/test/kotlin/mlid/enghub/hub/DatabaseTestResource.kt`
- Modify: `src/test/kotlin/mlid/enghub/hub/resource/FileResourceIT.kt:12,17`

- [ ] **Step 1: Create `DatabaseTestResource`**

Create `src/test/kotlin/mlid/enghub/hub/DatabaseTestResource.kt`:

```kotlin
package mlid.enghub.hub

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.oracle.OracleContainer

class DatabaseTestResource : QuarkusTestResourceLifecycleManager {
    private var mariadb: MariaDBContainer<*>? = null
    private var oracle: OracleContainer? = null

    override fun start(): Map<String, String> =
        if (System.getProperty("db") == "oracle") startOracle() else startMariaDb()

    private fun startMariaDb(): Map<String, String> {
        val container =
            MariaDBContainer("mariadb:10.11")
                .withDatabaseName("filehub")
                .withUsername("hub")
                .withPassword("hub")
        container.start()
        mariadb = container
        return mapOf(
            "quarkus.datasource.jdbc.url" to container.jdbcUrl,
            "quarkus.datasource.username" to container.username,
            "quarkus.datasource.password" to container.password,
            "quarkus.datasource.reactive.url" to
                "mysql://${container.host}:${container.getMappedPort(3306)}/filehub",
        )
    }

    private fun startOracle(): Map<String, String> {
        val container =
            OracleContainer("gvenzl/oracle-free:23-slim-faststart")
                .withDatabaseName("filehub")
                .withUsername("hub")
                .withPassword("hub")
        container.start()
        oracle = container
        return mapOf(
            "quarkus.datasource.jdbc.url" to container.jdbcUrl,
            "quarkus.datasource.username" to container.username,
            "quarkus.datasource.password" to container.password,
            "quarkus.datasource.reactive.url" to container.jdbcUrl.removePrefix("jdbc:"),
        )
    }

    override fun stop() {
        mariadb?.stop()
        oracle?.stop()
    }
}
```

The reactive Oracle URL is the JDBC URL minus the `jdbc:` prefix (`oracle:thin:@host:port/filehub`) — that is the format the Quarkus reactive Oracle client expects.

- [ ] **Step 2: Delete the old resource and update the IT**

```bash
git rm src/test/kotlin/mlid/enghub/hub/MariaDbTestResource.kt
```

In `FileResourceIT.kt`, change the import `mlid.enghub.hub.MariaDbTestResource` → `mlid.enghub.hub.DatabaseTestResource` and the annotation `@QuarkusTestResource(MariaDbTestResource::class)` → `@QuarkusTestResource(DatabaseTestResource::class)`.

- [ ] **Step 3: Verify the MariaDB IT path still passes**

Run: `./mvnw verify -DskipITs=false -Dit.test=FileResourceIT`
Expected: PASS (7 tests, MariaDB container — the default since failsafe gets `db=mariadb`).

- [ ] **Step 4: Commit**

```bash
./mvnw ktlint:format
git add src/test/kotlin
git commit -m "test: vendor-switchable DatabaseTestResource replaces MariaDbTestResource"
```

### Task 6: Run the IT suite against Oracle and fix what surfaces

This is the verification gate for every "verify during implementation" note in the spec: `?` placeholders on the Vert.x Oracle client, `Row.getLocalDateTime`/`getLong` mapping for `TIMESTAMP(6)`/`NUMBER(19)`, the Oracle DDL, and the Flyway Oracle module.

**Files:**
- Possibly modify: `src/main/kotlin/mlid/enghub/hub/repository/*.kt`, `src/main/resources/db/migration/oracle/V1__create_file_tables.sql`

- [ ] **Step 1: Run ITs against Oracle**

Run: `./mvnw verify -DskipITs=false -Ddb=oracle`
Expected: PASS (7 tests). First run pulls the `gvenzl/oracle-free:23-slim-faststart` image (~3 GB) and container startup takes 1–2 minutes — be patient before declaring failure.

Known failure modes and fixes:
- **Flyway "unsupported database" or missing Oracle support** → pin `flyway-database-oracle` to the `flyway-core` version (see Task 4 Step 2).
- **`ORA-...` syntax errors from repository queries** → the failing SQL is in the exception; the portable forms in Task 2 are believed Oracle-clean, but if the Oracle client rejects `?` placeholders, the error will say so — in that case only, convert that query's placeholders (this would contradict the spec assumption; flag it in the commit message).
- **`ClassCastException`/null from `toFileMetadata()`** → Oracle `NUMBER`/`TIMESTAMP` mapping issue; adjust the accessor in `toFileMetadata()` (e.g. `getValue(...)` + explicit conversion) for the affected column only.
- **`ORA-00904` on a DDL column** → typo in the Oracle migration; fix the migration file (it has never shipped, so editing V1 in place is fine).

- [ ] **Step 2: Re-run the MariaDB suite to confirm no Oracle fix regressed it**

Run: `./mvnw verify -DskipITs=false`
Expected: PASS.

- [ ] **Step 3: Commit (only if fixes were needed; otherwise skip)**

```bash
./mvnw ktlint:format
git add -A src/main
git commit -m "fix: Oracle compatibility fixes surfaced by integration tests"
```

### Task 7: Documentation

**Files:**
- Modify: `CLAUDE.md`
- Modify: `README.md` (if it has build/test instructions — check first; skip if not)

- [ ] **Step 1: Update CLAUDE.md**

In the Commands section, after the existing integration-test command, add:

```markdown
# Integration tests against Oracle instead of MariaDB
./mvnw verify -DskipITs=false -Ddb=oracle

# Build an Oracle-flavored artifact
./mvnw package -Ddb=oracle
```

In the Architecture section, update the Database subsection to mention dual-vendor support:

```markdown
The project supports **two database vendors**, selected at build time: MariaDB (default)
and Oracle 19c+ (`-Ddb=oracle`, which activates the `oracle` Maven profile). Repository
SQL is portable ANSI (OFFSET/FETCH pagination, INSERT…SELECT…WHERE NOT EXISTS instead of
INSERT IGNORE); only the Flyway DDL is vendor-specific (`db/migration/mariadb/` vs
`db/migration/oracle/`). Datasource config is injected via Maven resource filtering
(`@db.kind@` etc. in application.properties).
```

Also update the existing sentence "Integration tests require Docker (Testcontainers launches MariaDB, MinIO, and NATS)" to mention Oracle: "...launches MariaDB (or Oracle with `-Ddb=oracle`), MinIO, and NATS".

- [ ] **Step 2: Final full verification**

Run: `./mvnw verify -DskipITs=false`
Expected: PASS (includes ktlint check).

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "docs: document Oracle build and test commands"
```
