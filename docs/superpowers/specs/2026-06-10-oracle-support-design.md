# Oracle Database Support — Design

**Date:** 2026-06-10
**Status:** Approved

## Goal

Support both MariaDB (current) and Oracle 19c as the backing database, selectable at build time. MariaDB remains the default; all existing workflows are unchanged.

## Decisions

- **Scope:** Dual-vendor, selected at build/deploy time via Maven profiles. Not runtime-switchable (Quarkus fixes `db-kind` at build time).
- **Minimum Oracle version:** 19c.
- **Dialect strategy:** Portable ANSI SQL in repositories (no dialect abstraction, no per-vendor repository implementations). Only Flyway DDL is vendor-specific. MariaDB 10.11 (the pinned version) and Oracle 19c share `OFFSET ... ROWS FETCH NEXT ... ROWS ONLY` and `DUAL`, which makes this possible.
- **Testing:** Testcontainers for both vendors. MariaDB stays the default IT path; Oracle ITs run on demand via a flag.

## Build & vendor selection

Two Maven profiles, switched by the `db` system property (`-Ddb=oracle`; absent = MariaDB). Property activation rather than `-P` so the default vendor survives `-Pnative` builds, and the same property reaches the forked test JVM to switch the Testcontainers image.

- **`mariadb` (active when `db` ≠ `oracle`):** current dependencies — `quarkus-reactive-mysql-client`, `quarkus-jdbc-mariadb`, `flyway-mysql`.
- **`oracle` (`-Ddb=oracle`):** `quarkus-reactive-oracle-client`, `quarkus-jdbc-oracle`, plus the Flyway Oracle module (`flyway-database-oracle`).

Vendor-specific config is injected into `application.properties` via Maven resource filtering with `@`-only delimiters (so Quarkus `${ENV:default}` placeholders survive). Quarkus `%profile.` config blocks are not used because integration tests always run under the `test` profile and would never see them. Filtered values:

- `quarkus.datasource.db-kind`
- JDBC and reactive URL defaults
- `quarkus.flyway.locations`

One artifact per vendor.

## Repository SQL changes (portable rewrites)

All changes keep the existing files and interfaces; no Kotlin abstractions are added.

1. **`FileMetadataRepository.search()` and `findMissing()`:**
   `LIMIT ? OFFSET ?` → `OFFSET ? ROWS FETCH NEXT ? ROWS ONLY`.
   Note the bind-parameter order swaps: offset first, then page size.

2. **`FileDeliveryRepository.insertIgnore()`:**
   `INSERT IGNORE INTO file_delivery (...) VALUES (?,?,?,?,?)` →

   ```sql
   INSERT INTO file_delivery (id, file_id, consumer_id, note, processed_at)
   SELECT ?, ?, ?, ?, ? FROM DUAL
   WHERE NOT EXISTS (
       SELECT 1 FROM file_delivery WHERE file_id = ? AND consumer_id = ?
   )
   ```

   Two extra bind parameters (fileId, consumerId). Semantics match `INSERT IGNORE` for the duplicate case: zero rows inserted, no error. The `uq_delivery` unique constraint remains on both vendors as a backstop against concurrent inserts.

3. **Placeholders:** `?` stays — both the Vert.x MySQL and Oracle reactive clients accept JDBC-style placeholders.

**Row-mapping caveat to verify during implementation:** the Oracle client must return `TIMESTAMP(6)` via `Row.getLocalDateTime` and `NUMBER(19)` via `Row.getLong` as the MariaDB client does, so `toFileMetadata()` works unchanged. The Oracle integration test proves this.

## Flyway DDL (vendor-specific)

Migrations move from `db/migration/` to vendor directories:

- `db/migration/mariadb/V1__create_file_tables.sql` — current script, unchanged content.
- `db/migration/oracle/V1__create_file_tables.sql` — same schema with Oracle types:

| MariaDB | Oracle 19c |
|---|---|
| `VARCHAR(n)` | `VARCHAR2(n)` |
| `BIGINT` | `NUMBER(19)` |
| `DATETIME(6)` | `TIMESTAMP(6)` |
| `TEXT` | `CLOB` |
| `JSON` | `VARCHAR2(4000) CHECK (tags IS JSON)` |
| `ENUM('REGISTERED','FAILED')` | `VARCHAR2(10) CHECK (status IN ('REGISTERED','FAILED'))` |

`quarkus.flyway.locations` points at the right directory per Quarkus config profile.

## Testing

- **Unit tests:** untouched (hand-written fakes, no DB).
- **MariaDB ITs:** existing `FileResourceIT` + `MariaDbTestResource` stay the default path for `./mvnw verify -DskipITs=false`.
- **Oracle ITs:** `MariaDbTestResource` is replaced by a single `DatabaseTestResource` that starts either container based on the `db` system property; Oracle uses the `gvenzl/oracle-free` image (23ai — no free 19c image exists; the SQL is 19c-compatible by construction, ~1–2 min startup). Activated via `./mvnw verify -DskipITs=false -Ddb=oracle`, running the same IT suite against Oracle. Not part of the default local/CI run.
- **Docs:** CLAUDE.md and README updated with the new build/test commands.

## Out of scope

- Runtime vendor switching in a single artifact.
- Changes to service logic, NATS publishing, MinIO verification, or the resource layer.
- The separate `status-gui` TypeScript project.
