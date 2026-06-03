# Status Query GUI (TypeScript) — Design Spec

**Date:** 2026-06-04
**Status:** Approved

## Overview

A standalone Node.js/Express web application (`status-gui-ts/`) that gives ops/support teams a browser-based UI to query file status directly from MariaDB. It is the TypeScript counterpart to `status-gui/` (Kotlin/Ktor) and lives alongside it in the same repository. Both apps share the same database but run as independent services with their own deploy lifecycles.

## Goals

- Ops/support can search files by uploader/bucket/status/date, view file detail with delivery records, and find files a consumer has not yet processed.
- Optional OIDC authentication — enabled when IdP env vars are present, disabled (open) otherwise.
- GUI deploys never restart or affect the Quarkus or Kotlin runtimes.

## Architecture

```
┌─────────────────────────┐   ┌─────────────────────────┐   ┌─────────────────────────┐
│   file-exchange-hub     │   │   status-gui (Kotlin)   │   │   status-gui-ts (Node)  │
│   (Quarkus / Kotlin)    │   │   (Ktor / Kotlin)       │   │   (Express / TS)        │
│   Port 8080             │   │   Port 8090             │   │   Port 8091             │
│   Read + Write          │   │   Read-only             │   │   Read-only             │
└──────────┬──────────────┘   └──────────┬──────────────┘   └──────────┬──────────────┘
           │                             │                             │
           └─────────────────────────────┴─────────────────────────────┘
                                         ▼
                         ┌─────────────────────────┐
                         │   MariaDB               │
                         │   file_metadata         │
                         │   file_delivery         │
                         └─────────────────────────┘
```

- `status-gui-ts` connects with the same read-only MariaDB user (`gui_reader`) granted `SELECT` only on `file_metadata` and `file_delivery`.
- No HTTP calls between any of the three apps.

## Project Structure

```
status-gui-ts/
├── package.json
├── tsconfig.json
├── jest.config.ts
└── src/
    ├── index.ts                          # starts server with real repo
    ├── app.ts                            # createApp(repo) — Express factory
    ├── config.ts                         # env var loading and validation
    ├── db.ts                             # mysql2 pool from env vars
    ├── repository/
    │   ├── types.ts                      # FileRow, DeliveryRow, PagedResult, FileQueryRepository interface
    │   └── MysqlFileQueryRepository.ts   # raw SQL queries via mysql2
    ├── routes/
    │   ├── fileSearch.ts                 # GET / → redirect, GET /files, GET /files/:id
    │   └── missing.ts                    # GET /missing
    └── views/                            # EJS templates
        ├── layout.ejs                    # shared nav + inline CSS
        ├── fileSearch.ejs
        ├── fileDetail.ejs
        └── missing.ejs
test/
├── FakeFileQueryRepository.ts
├── routes/
│   ├── fileSearch.test.ts               # supertest + fake repo
│   └── missing.test.ts
└── repository/
    └── MysqlFileQueryRepository.test.ts  # Testcontainers MariaDB
```

## Dependencies

| Concern | Library |
|---|---|
| HTTP server | `express` |
| HTML templating | `ejs` |
| DB connection pool | `mysql2` (promise API) |
| OIDC authentication | `express-openid-connect` |
| Env var loading (dev) | `dotenv` |

**Dev dependencies:** `typescript`, `tsx`, `ts-jest`, `jest`, `supertest`, `testcontainers`, `@types/express`, `@types/supertest`, `@types/ejs`

## Pages and Routes

### Root — `GET /`

Redirects to `/files`.

### File Search — `GET /files`

Filter form with four optional parameters:

| Parameter | Input | Maps to |
|---|---|---|
| `uploaderId` | Text field | `file_metadata.uploader_id` |
| `bucket` | Text field | `file_metadata.bucket` |
| `status` | Dropdown: any / REGISTERED / FAILED | `file_metadata.status` |
| `since` | Date picker | `file_metadata.registered_at >=` |

Results rendered as a paginated table (`page`, `size=20`). Columns: ID (link), Filename, Bucket, Uploader, Status (badge), Registered At. Malformed `since` value is silently ignored. All parameters passed as query string values — every search is bookmarkable.

### File Detail — `GET /files/:id`

Two-column metadata grid showing all `file_metadata` fields. Below the grid, a Delivery Records table lists every row in `file_delivery` for this file (consumer ID, processed_at, note). Returns 404 if the ID is not found.

### Missing Files — `GET /missing`

| Parameter | Required | Behavior |
|---|---|---|
| `consumerId` | Yes | consumers not in `file_delivery` for this file |
| `bucket` | No | `file_metadata.bucket` filter |
| `since` | No | defaults to now minus 24 hours |

No `consumerId` → renders form with prompt, no DB call made. Results use a `LEFT JOIN` between `file_metadata` and `file_delivery` filtered to rows where the delivery join produces no match for the given `consumerId`.

## App Factory Pattern

`app.ts` exports `createApp(repo: FileQueryRepository): Express`. This accepts a repository instance at construction time:

- **Production** (`index.ts`): creates the mysql2 pool, constructs `MysqlFileQueryRepository`, calls `createApp(repo)`
- **Tests**: constructs `FakeFileQueryRepository` with preset results, calls `createApp(fakeRepo)`

This eliminates any need to mock `mysql2` or the database in route tests.

## Configuration

Connection details and OIDC settings passed via environment variables:

```
DB_HOST=<host>
DB_PORT=3306
DB_NAME=<database>
DB_USER=gui_reader
DB_PASS=<password>
GUI_PORT=8091              # optional, default 8091

# OIDC — all four required to enable; any absent = app runs open
OIDC_ISSUER_BASE_URL=https://your-idp.example.com
OIDC_CLIENT_ID=<client-id>
OIDC_CLIENT_SECRET=<client-secret>
OIDC_BASE_URL=http://localhost:8091
```

`config.ts` validates required DB vars at startup and throws with a clear message if any are missing. OIDC vars are all-or-nothing: if all four are set the middleware is installed and all routes are protected; if any is absent the app starts without auth.

## OIDC Authentication

When enabled, `express-openid-connect` middleware is installed on the Express app before any routes. Unauthenticated requests are redirected to the IdP. The middleware handles the callback route, session cookie, and token refresh automatically. Routes contain no auth logic.

## Database Access

### Read-only user

Same `gui_reader` user as the Kotlin app:

```sql
CREATE USER 'gui_reader'@'%' IDENTIFIED BY '<password>';
GRANT SELECT ON <db>.file_metadata TO 'gui_reader'@'%';
GRANT SELECT ON <db>.file_delivery TO 'gui_reader'@'%';
```

### Key queries

**Search** — parameterized WHERE clause built from non-null filter values, `ORDER BY registered_at DESC`, `LIMIT`/`OFFSET` for pagination.

**Detail** — `SELECT * FROM file_metadata WHERE id = ?` plus `SELECT * FROM file_delivery WHERE file_id = ?`.

**Missing** — `SELECT fm.* FROM file_metadata fm LEFT JOIN file_delivery fd ON fd.file_id = fm.id AND fd.consumer_id = ? WHERE fd.id IS NULL [AND fm.bucket = ?] AND fm.registered_at >= ? ORDER BY fm.registered_at DESC LIMIT ? OFFSET ?`.

## UI Design

- **Server-side rendered** HTML via EJS — no JavaScript framework, no build step for the frontend.
- **Pure HTML GET forms** — every search is bookmarkable; browser back button works naturally.
- **Status badges** — green pill for REGISTERED, red pill for FAILED.
- **Pagination** — prev/next links with `page` query parameter; page size fixed at 20.
- **Minimal styling** — inline CSS in `layout.ejs`; consistent with the Kotlin version's visual design.

## Error Handling

- File not found on detail page → 404 page with message and link back to search.
- DB connection failure → 500 page with a generic error message; no stack trace exposed to browser.
- Missing required `consumerId` on `/missing` → form re-renders with prompt, no DB call.
- Malformed `since` date → silently ignored, treated as absent.

## Testing

- **Route tests** (`supertest` + `FakeFileQueryRepository`): no DB required, fast, test all routes and edge cases.
- **Repository integration tests** (`testcontainers` MariaDB): seed fixture rows, verify all query methods including the LEFT JOIN anti-join for `findMissing`.
- No mocking of `mysql2` — consistent with the project's test strategy.

## Build and Run

```bash
# Dev (hot reload)
npm run dev       # tsx watch src/index.ts

# Production build
npm run build     # tsc
npm start         # node dist/index.js

# Tests
npm test          # jest
```

## Out of Scope

- Write operations — GUI is read-only by design.
- Charts or aggregate dashboards — plain tabular queries only.
- Sharing session state with the Kotlin status-gui.
