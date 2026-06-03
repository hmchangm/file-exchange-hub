# Status Query GUI — Design Spec

**Date:** 2026-06-03  
**Status:** Approved

## Overview

A separate Kotlin/Ktor web application that gives ops/support teams a browser-based UI to query file status directly from the MariaDB database. It lives as a subdirectory (`status-gui/`) in this repo but runs as an independent service with its own deploy lifecycle, completely decoupled from the Quarkus core.

## Goals

- Ops/support can look up a file by ID, search files by uploader/bucket/status/date, and check which consumers have processed a file.
- Ops/support can identify files that a given consumer has not yet processed.
- GUI deploys never restart or affect the Quarkus runtime.

## Architecture

```
┌─────────────────────────┐   ┌─────────────────────────┐
│   file-exchange-hub     │   │   status-gui            │
│   (Quarkus / Kotlin)    │   │   (Ktor / Kotlin)       │
│   Port 8080             │   │   Port 8090             │
│   Read + Write          │   │   Read-only             │
└──────────┬──────────────┘   └──────────┬──────────────┘
           │                             │
           └──────────────┬──────────────┘
                          ▼
          ┌─────────────────────────┐
          │   MariaDB               │
          │   file_metadata         │
          │   file_delivery         │
          └─────────────────────────┘
```

- `status-gui` connects with a **read-only MariaDB user** (`gui_reader`) granted `SELECT` only on `file_metadata` and `file_delivery`.
- The two apps share the same database but have independent lifecycles.
- No HTTP calls between the apps — the GUI does not depend on the Quarkus API being up.

## Project Structure

```
status-gui/
├── pom.xml
└── src/main/kotlin/mlid/enghub/statusgui/
    ├── Main.kt                    # embeddedServer entry point
    ├── Database.kt                # HikariCP connection pool setup
    ├── routing/
    │   ├── FileSearchRoutes.kt    # GET /files, GET /files/{id}
    │   └── MissingFilesRoutes.kt  # GET /missing
    ├── repository/
    │   ├── FileQueryRepository.kt # SQL queries against file_metadata + file_delivery
    │   └── model/                 # read-only data classes (FileRow, DeliveryRow)
    └── templates/
        ├── Layout.kt              # shared nav + page shell (kotlinx.html)
        ├── FileSearchPage.kt      # search form + results table
        ├── FileDetailPage.kt      # metadata grid + delivery table
        └── MissingFilesPage.kt    # missing files form + results
```

## Dependencies

| Concern | Library |
|---|---|
| HTTP server | `ktor-server-netty` |
| HTML templating | `kotlinx.html` (server-side) |
| DB connection pool | HikariCP |
| SQL queries | Kotlin Exposed (`exposed-jdbc`) |
| MariaDB driver | `mariadb-java-client` |

## Pages and Routes

### File Search — `GET /files`

Filter form with four optional parameters:

| Parameter | Input | Maps to |
|---|---|---|
| `uploaderId` | Text field | `file_metadata.uploader_id` |
| `bucket` | Text field | `file_metadata.bucket` |
| `status` | Dropdown: any / REGISTERED / FAILED | `file_metadata.status` |
| `since` | Date picker | `file_metadata.registered_at >=` |

Results rendered as a paginated table (`page`, `size=20`). Columns: ID (link), Filename, Bucket, Uploader, Status (badge), Registered At.

All parameters are passed as query string values, making every search result a bookmarkable URL.

### File Detail — `GET /files/{id}`

Two-column metadata grid showing all `file_metadata` fields. Below the grid, a **Delivery Records** table lists every row in `file_delivery` for this file (consumer ID, processed_at, note). A "← Back" link navigates to `/files`.

### Missing Files — `GET /missing`

Filter form:

| Parameter | Required | Maps to |
|---|---|---|
| `consumerId` | Yes | consumers not in `file_delivery` for this file |
| `bucket` | No | `file_metadata.bucket` |
| `since` | No | `file_metadata.registered_at >=` (defaults to last 24 h) |

Results table: same columns as File Search. The underlying query is a `LEFT JOIN` between `file_metadata` and `file_delivery` filtered to rows where the `file_delivery` join produces no match for the given `consumerId`.

## Database Access

### Read-only user

```sql
CREATE USER 'gui_reader'@'%' IDENTIFIED BY '<password>';
GRANT SELECT ON <db>.file_metadata TO 'gui_reader'@'%';
GRANT SELECT ON <db>.file_delivery TO 'gui_reader'@'%';
```

### Configuration

Connection details passed via environment variables:

```
DB_HOST=<host>
DB_PORT=3306
DB_NAME=<database>
DB_USER=gui_reader
DB_PASS=<password>
GUI_PORT=8090        # optional, default 8090
```

### Key queries

**Search** — dynamic WHERE clause built from non-null filter values, ORDER BY `registered_at DESC`, LIMIT/OFFSET for pagination.

**Detail** — `SELECT * FROM file_metadata WHERE id = ?` joined with `SELECT * FROM file_delivery WHERE file_id = ?`.

**Missing** — `SELECT fm.* FROM file_metadata fm LEFT JOIN file_delivery fd ON fd.file_id = fm.id AND fd.consumer_id = ? WHERE fd.id IS NULL [AND fm.bucket = ?] [AND fm.registered_at >= ?] ORDER BY fm.registered_at DESC`.

## UI Design

- **Server-side rendered** HTML via `kotlinx.html` — no JavaScript framework, no build step.
- **Pure HTML GET forms** — every search is bookmarkable; browser back button works naturally.
- **Status badges** — green pill for REGISTERED, red pill for FAILED.
- **Pagination** — prev/next links with `page` query parameter; page size fixed at 20.
- **Minimal styling** — inline CSS in `Layout.kt`; no external CSS framework required.

## Error Handling

- File not found on detail page → 404 page with a message and link back to search.
- DB connection failure → 500 page with a plain error message; no stack trace exposed to browser.
- Missing required `consumerId` on `/missing` → form re-renders with a validation message, no DB call made.

## Testing

- Unit-test `FileQueryRepository` with an in-process H2 (or Testcontainers MariaDB) database seeded with fixture rows.
- Integration-test each route with `ktor-server-test-host` and a Testcontainers MariaDB instance.
- No mocking of the database — consistent with the test strategy in the core module.

## Out of Scope

- Authentication / access control (ops environment is assumed to be network-restricted).
- Write operations (mark processed, register files) — GUI is read-only by design.
- Charts or aggregate dashboards — plain tabular queries only.
