# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Dev mode (hot reload)
./mvnw quarkus:dev

# Compile
./mvnw compile

# Unit tests only (no containers required)
./mvnw test

# Unit + integration tests (spins up Testcontainers)
./mvnw verify -DskipITs=false

# Run a single test class
./mvnw test -Dtest=FileRegistrationServiceTest

# Run a single integration test class
./mvnw verify -DskipITs=false -Dit.test=FileResourceIT
```

Integration tests require Docker (Testcontainers launches MariaDB, MinIO, and NATS).

## Architecture

**Quarkus 3.24 / Kotlin 2.2 / Java 17.** All service and resource methods are `suspend` functions using Kotlin coroutines. Reactive database calls use `awaitSuspending()` to bridge the Mutiny reactive API into coroutines.

### Request flow

```
FileResource (REST, suspend)
  └─ FileRegistrationService
       ├─ MinioVerifier (ObjectStoreVerifier) — sync S3 HEAD check, called via withContext(Dispatchers.IO)
       ├─ FileMetadataRepository (FileMetadataStore) — reactive MySQL pool, suspend
       └─ FileEventPublisher (FileRegistrationEventPublisher) — NATS JetStream MutinyEmitter, suspend
```

NATS publish is wrapped in `withTimeoutOrNull(2000ms)` — if it times out, registration still succeeds and `eventPublished=false` is returned.

### Key interfaces

The service layer depends on interfaces, not concrete implementations:

| Interface | Implementation |
|---|---|
| `ObjectStoreVerifier` | `MinioVerifier` |
| `FileMetadataStore` | `FileMetadataRepository` |
| `FileRegistrationEventPublisher` | `FileEventPublisher` |

Unit tests (`FileRegistrationServiceTest`) use hand-written fakes. Integration tests (`FileResourceIT`) use `@QuarkusTestResource` to spin up real containers.

### Database

Two tables: `file_metadata` and `file_delivery`. The project uses **two datasource connections**: a JDBC datasource (Flyway migrations only) and a reactive datasource (all runtime queries via `io.vertx.mutiny.sqlclient.Pool`).

Tags are stored as a JSON string in `file_metadata.tags` and deserialized at the resource layer with `kotlinx.serialization`.

### Packages

- `domain/` — plain data classes (`FileMetadata`, `FileDelivery`, `FileRegisteredEvent`)
- `repository/` — database access; `FileMetadataStore` is the injectable interface
- `service/` — business logic and interfaces for external dependencies
- `resource/` — JAX-RS endpoints and `dto/` request/response types; `ApiExceptionMapper` maps domain exceptions to HTTP responses
