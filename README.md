# File Exchange Hub

Quarkus/Kotlin service for registering file metadata after confirming the
object exists in MinIO, storing the metadata in MariaDB, and publishing a NATS
JetStream event for downstream consumers.

## Stack

- Kotlin 2.2, Java 17
- Quarkus 3.24
- REST with Kotlin serialization and suspend endpoints
- MariaDB with Flyway migrations
- Reactive MySQL client
- MinIO through the S3 client
- NATS JetStream reactive messaging
- Testcontainers for integration tests

## Runtime Flow

1. `POST /api/files/register` receives file metadata.
2. The service checks the object exists in MinIO using `HEAD`.
3. Metadata is inserted into MariaDB.
4. A `files.registered` event is published to NATS JetStream.

Database persistence happens before event publication. If event publication
does not complete in time, registration still succeeds with
`eventPublished=false`.

## Configuration

Defaults are defined in `src/main/resources/application.properties`.

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_USER` | `hub` | MariaDB user |
| `DB_PASSWORD` | `hub` | MariaDB password |
| `DB_URL` | `jdbc:mariadb://localhost:3306/filehub` | JDBC URL for Flyway |
| `DB_REACTIVE_URL` | `mariadb://localhost:3306/filehub` | Reactive MariaDB URL |
| `MINIO_URL` | `http://localhost:9000` | MinIO/S3 endpoint |
| `MINIO_ACCESS_KEY` | `minioadmin` | MinIO access key |
| `MINIO_SECRET_KEY` | `minioadmin` | MinIO secret key |
| `NATS_URL` | `nats://localhost:4222` | NATS server URL |

NATS publishes to:

- channel: `files-registered`
- subject: `files.registered`
- stream: `FILES`

## API

### Register File

```http
POST /api/files/register
Content-Type: application/json
```

```json
{
  "bucket": "incoming",
  "reportId": "WXG",
  "reportCategory": "AVI",
  "objectKey": "reports/report.pdf",
  "filename": "report.pdf",
  "contentType": "application/pdf",
  "fileSize": 1024,
  "checksum": "d41d8cd98f00b204e9800998ecf8427e",
  "uploaderId": "client-A",
  "tags": {
    "dept": "finance"
  }
}
```

Returns `201`:

```json
{
  "id": "generated-file-id",
  "status": "REGISTERED",
  "eventPublished": true,
  "registeredAt": "2026-05-03T13:00:00Z"
}
```

If the object does not exist in MinIO, returns `404` with
`code=OBJECT_NOT_FOUND`.

### Get File

```http
GET /api/files/{id}
```

Returns file metadata, or `404` with `code=FILE_NOT_FOUND`.

### Search Files

```http
GET /api/files?uploaderId=client-A&bucket=incoming&page=0&size=20
```

Returns:

```json
{
  "files": [],
  "total": 0,
  "page": 0,
  "size": 20
}
```

### Find Missing Deliveries

```http
GET /api/files/missing?consumerId=consumer-A&bucket=incoming&since=2026-05-03T00:00:00Z&page=0&size=20
```

Returns registered files that have not been marked processed by the given
consumer.

### Mark Processed

```http
PUT /api/files/{id}/delivery
Content-Type: application/json
```

```json
{
  "consumerId": "consumer-A",
  "note": "processed successfully"
}
```

The operation is idempotent and returns `200`.

## Development

Run in dev mode:

```bash
./mvnw quarkus:dev
```

Compile:

```bash
./mvnw compile
```

Run unit tests:

```bash
./mvnw test
```

Run unit and integration tests:

```bash
./mvnw verify -DskipITs=false
```

Integration tests use Testcontainers for MariaDB, MinIO, and NATS.

## Database

Flyway runs at application startup. The initial migration creates:

- `file_metadata`
- `file_delivery`

Migration files live in `src/main/resources/db/migration/`.
