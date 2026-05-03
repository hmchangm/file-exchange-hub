# File Exchange Hub — Design Spec

**Date:** 2026-05-03  
**Stack:** Kotlin 2.2 · Quarkus 3.24.5 · MinIO (S3) · NATS JetStream · MariaDB · Java 17

---

## Overview

A single Quarkus service (`file-exchange-hub`) that acts as a metadata registry and event broker for file exchange between external clients and downstream consumers.

Clients upload files directly to MinIO using their own service accounts, then register the file with the hub via REST API. The hub verifies the file exists in MinIO, persists the metadata to MariaDB, and publishes a NATS JetStream event so downstream consumers are notified. Consumers access files directly from MinIO using their own credentials. Consumers can query the hub for files they may have missed and mark files as processed.

---

## Architecture

```
External Client
      │
      ├─► MinIO (direct upload via service account)
      │
      └─► POST /api/files/register
               │
               ▼
        FileRegistrationResource   (RESTEasy Reactive)
               │
               ▼
        FileRegistrationService
          ├── MinioVerifier         → HEAD object check (quarkus-amazon-s3)
          ├── FileMetadataRepository → persist to MariaDB (Panache + Hibernate)
          └── FileEventPublisher    → publish to NATS JetStream
               │
               ▼
        NATS JetStream stream: FILES
          subject: files.registered
               │
               ▼
        Downstream consumers (subscribe independently, access MinIO directly)
```

---

## Components

| Component | Responsibility |
|---|---|
| `FileRegistrationResource` | REST endpoint, input validation |
| `FileRegistrationService` | Orchestrates verify → persist → publish |
| `MinioVerifier` | HEAD request to confirm object exists in MinIO |
| `FileMetadataRepository` | Panache repository for `file_metadata` table |
| `FileDeliveryRepository` | Panache repository for `file_delivery` table |
| `FileEventPublisher` | Publishes `FileRegisteredEvent` to NATS JetStream |

---

## Data Model

### `file_metadata`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | generated on registration |
| `bucket` | VARCHAR(255) | MinIO bucket name |
| `object_key` | VARCHAR(1024) | full path in bucket |
| `filename` | VARCHAR(255) | original filename |
| `content_type` | VARCHAR(128) | e.g. `application/pdf` |
| `file_size` | BIGINT | bytes |
| `uploader_id` | VARCHAR(255) | client-supplied identifier |
| `tags` | JSON | optional key-value labels |
| `status` | ENUM(`REGISTERED`, `FAILED`) | set after MinIO verification |
| `registered_at` | TIMESTAMP | server-set on creation |

Index on `(registered_at, bucket, status)` for missing-file queries.

### `file_delivery`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | |
| `file_id` | UUID (FK → `file_metadata`) | |
| `consumer_id` | VARCHAR(255) | self-reported by consumer |
| `note` | TEXT | optional (job ID, error reason, etc.) |
| `processed_at` | TIMESTAMP | server-set |

Unique constraint on `(file_id, consumer_id)`. Index on `file_id`.

---

## REST API

### Register a file
```
POST /api/files/register
```
Request body:
```json
{
  "bucket": "incoming",
  "objectKey": "reports/2026/may/report.pdf",
  "filename": "report.pdf",
  "contentType": "application/pdf",
  "fileSize": 204800,
  "uploaderId": "client-system-A",
  "tags": { "department": "finance" }
}
```
Response `201`:
```json
{
  "id": "uuid",
  "status": "REGISTERED",
  "eventPublished": true,
  "registeredAt": "2026-05-03T10:00:00Z"
}
```
- `404` if object not found in MinIO
- `503` if MinIO is unreachable
- `422` if request validation fails
- `201` with `"eventPublished": false` if NATS publish fails (file is still registered)

### Get file metadata
```
GET /api/files/{id}
```
Response `200` with full metadata. `404` if not found.

### Search files
```
GET /api/files?uploaderId=&bucket=&page=0&size=20
```
Paginated, ordered by `registered_at` descending.

### Query missing files (consumer catch-up)
```
GET /api/files/missing?consumerId=consumer-B&bucket=incoming&since=2026-05-01T00:00:00Z&page=0&size=20
```
Returns files registered after `since` (defaults to 24 hours ago) with no `file_delivery` row for the given `consumerId`. Ordered by `registered_at` ascending so consumers process in order.

Response `200`:
```json
{
  "files": [ { ...file metadata... } ],
  "total": 5,
  "page": 0,
  "size": 20
}
```

### Mark file as processed
```
PUT /api/files/{id}/delivery
```
Request body:
```json
{ "consumerId": "consumer-B", "note": "processed by job-123" }
```
- `200 OK` (idempotent — re-marking is a no-op)
- `404` if file not found

---

## NATS JetStream Event

**Stream:** `FILES`  
**Subject:** `files.registered`  
**Retention:** limits-based (configure max age/size per environment)

Payload:
```json
{
  "id": "uuid",
  "bucket": "incoming",
  "objectKey": "reports/2026/may/report.pdf",
  "filename": "report.pdf",
  "contentType": "application/pdf",
  "fileSize": 204800,
  "uploaderId": "client-system-A",
  "tags": { "department": "finance" },
  "registeredAt": "2026-05-03T10:00:00Z"
}
```

---

## Error Handling

| Failure | Behavior |
|---|---|
| MinIO HEAD → 404 | `404` returned; nothing written to DB or NATS |
| MinIO unreachable | `503` returned; nothing written to DB or NATS |
| MariaDB write fails | `500` returned; NATS event NOT published |
| NATS publish fails | DB row committed; `201` returned with `"eventPublished": false`; consumer uses missing API to catch up |
| Duplicate `PUT /delivery` | Idempotent `200`; no error |

DB commit always precedes NATS publish. The DB is the source of truth; NATS is best-effort with the missing API as the safety net.

**Global error response shape:**
```json
{ "error": "human-readable message", "code": "OBJECT_NOT_FOUND" }
```

---

## Testing Strategy

- **Unit tests** — `FileRegistrationService` with mocked `MinioVerifier`, `FileMetadataRepository`, `FileEventPublisher`
- **Integration tests** — `@QuarkusTest` with Testcontainers:
  - MinIO (`testcontainers:minio`)
  - MariaDB (`testcontainers:mariadb`)
  - NATS (`nats` Docker image)
- **Key scenarios:**
  - File not found in MinIO → `404`, nothing persisted
  - MinIO unreachable → `503`
  - NATS down → `201` with `eventPublished: false`, file queryable via missing API
  - Duplicate `PUT /delivery` → idempotent `200`
  - Missing query returns only unprocessed files for the requesting consumer
  - Missing query respects `since` and `bucket` filters

---

## Dependencies

```xml
quarkus-resteasy-reactive-kotlin-serialization
quarkus-kotlin
quarkus-arc
quarkus-hibernate-orm-panache-kotlin
quarkus-jdbc-mariadb
quarkus-amazon-s3                          <!-- quarkiverse, version 3.x -->
quarkus-messaging-nats-jetstream
quarkus-smallrye-health
quarkus-junit5                             <!-- test -->
testcontainers:minio                       <!-- test -->
testcontainers:mariadb                     <!-- test -->
```
