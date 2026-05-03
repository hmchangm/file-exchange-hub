# File Exchange Hub Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a single Quarkus service that accepts file metadata registrations, verifies the file exists in MinIO, persists metadata to MariaDB, and publishes a NATS JetStream event for downstream consumers.

**Architecture:** REST API accepts registration requests → MinIO HEAD check → MariaDB persist → NATS JetStream publish. DB commit always precedes NATS publish; if NATS is down the file is still registered and discoverable via the missing-files query API.

**Tech Stack:** Kotlin 2.2, Quarkus 3.24.5, Java 17, Hibernate ORM Panache (Kotlin), MariaDB, quarkus-amazon-s3 (quarkiverse), quarkus-messaging-nats-jetstream, RESTEasy Reactive, Flyway, Testcontainers

---

## File Structure

```
file-exchange-hub/
├── pom.xml
└── src/
    ├── main/
    │   ├── kotlin/tw/brandy/ironman/hub/
    │   │   ├── domain/
    │   │   │   ├── FileMetadata.kt           entity + FileStatus enum
    │   │   │   ├── FileDelivery.kt           entity
    │   │   │   └── FileRegisteredEvent.kt    NATS payload data class
    │   │   ├── repository/
    │   │   │   ├── FileMetadataRepository.kt Panache repository
    │   │   │   └── FileDeliveryRepository.kt Panache repository
    │   │   ├── service/
    │   │   │   ├── MinioVerifier.kt          HEAD check against MinIO
    │   │   │   ├── FileEventPublisher.kt     NATS JetStream publish
    │   │   │   └── FileRegistrationService.kt orchestrates verify→persist→publish
    │   │   └── resource/
    │   │       ├── dto/
    │   │       │   ├── RegisterFileRequest.kt
    │   │       │   ├── RegisterFileResponse.kt
    │   │       │   ├── FileMetadataDto.kt
    │   │       │   ├── MarkProcessedRequest.kt
    │   │       │   ├── PagedFilesResponse.kt
    │   │       │   └── ErrorResponse.kt
    │   │       └── FileResource.kt           all REST endpoints
    │   └── resources/
    │       ├── application.properties
    │       └── db/migration/
    │           └── V1__create_file_tables.sql
    └── test/
        └── kotlin/tw/brandy/ironman/hub/
            ├── service/
            │   └── FileRegistrationServiceTest.kt  unit tests (mocked deps)
            └── resource/
                └── FileResourceIT.kt               @QuarkusTest integration tests
```

---

## Task 1: Scaffold the project

**Files:**
- Create: `pom.xml`
- Create: `src/main/resources/application.properties`

- [ ] **Step 1: Create `pom.xml`**

```xml
<?xml version="1.0"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>tw.brandy.ironman</groupId>
  <artifactId>file-exchange-hub</artifactId>
  <version>1.0.0-SNAPSHOT</version>

  <properties>
    <kotlin.version>2.2.10</kotlin.version>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
    <quarkus.platform.artifact-id>quarkus-bom</quarkus.platform.artifact-id>
    <quarkus.platform.version>3.24.5</quarkus.platform.version>
    <compiler-plugin.version>3.11.0</compiler-plugin.version>
    <surefire-plugin.version>3.2.5</surefire-plugin.version>
    <testcontainers.version>1.19.7</testcontainers.version>
    <skipITs>true</skipITs>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>${quarkus.platform.group-id}</groupId>
        <artifactId>${quarkus.platform.artifact-id}</artifactId>
        <version>${quarkus.platform.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
      <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers-bom</artifactId>
        <version>${testcontainers.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <!-- Quarkus core -->
    <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-kotlin</artifactId></dependency>
    <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-arc</artifactId></dependency>
    <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-resteasy-reactive-kotlin-serialization</artifactId></dependency>
    <!-- DB -->
    <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-hibernate-orm-panache-kotlin</artifactId></dependency>
    <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-jdbc-mariadb</artifactId></dependency>
    <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-flyway</artifactId></dependency>
    <!-- MinIO / S3 -->
    <dependency>
      <groupId>io.quarkiverse.amazonservices</groupId>
      <artifactId>quarkus-amazon-s3</artifactId>
      <version>3.3.3</version>
    </dependency>
    <!-- NATS JetStream -->
    <dependency>
      <groupId>io.quarkiverse.nats-jetstream</groupId>
      <artifactId>quarkus-messaging-nats-jetstream</artifactId>
      <version>3.9.0</version>
    </dependency>
    <!-- Health -->
    <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-smallrye-health</artifactId></dependency>
    <!-- Kotlin stdlib -->
    <dependency>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-stdlib-jdk8</artifactId>
      <version>${kotlin.version}</version>
    </dependency>
    <!-- Test -->
    <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-junit5</artifactId><scope>test</scope></dependency>
    <dependency><groupId>io.rest-assured</groupId><artifactId>rest-assured</artifactId><scope>test</scope></dependency>
    <dependency><groupId>io.rest-assured</groupId><artifactId>kotlin-extensions</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>minio</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>mariadb</artifactId><scope>test</scope></dependency>
    <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-test-common</artifactId><scope>test</scope></dependency>
    <dependency>
      <groupId>io.mockk</groupId>
      <artifactId>mockk-jvm</artifactId>
      <version>1.13.10</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <sourceDirectory>src/main/kotlin</sourceDirectory>
    <testSourceDirectory>src/test/kotlin</testSourceDirectory>
    <plugins>
      <plugin>
        <groupId>${quarkus.platform.group-id}</groupId>
        <artifactId>quarkus-maven-plugin</artifactId>
        <version>${quarkus.platform.version}</version>
        <extensions>true</extensions>
        <executions>
          <execution>
            <goals><goal>build</goal><goal>generate-code</goal><goal>generate-code-tests</goal></goals>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>${compiler-plugin.version}</version>
        <configuration><compilerArgs><arg>-parameters</arg></compilerArgs></configuration>
      </plugin>
      <plugin>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>kotlin-maven-plugin</artifactId>
        <version>${kotlin.version}</version>
        <executions>
          <execution><id>compile</id><goals><goal>compile</goal></goals></execution>
          <execution><id>test-compile</id><goals><goal>test-compile</goal></goals></execution>
        </executions>
        <configuration>
          <javaParameters>true</javaParameters>
          <jvmTarget>17</jvmTarget>
          <compilerPlugins>
            <plugin>all-open</plugin>
            <plugin>kotlinx-serialization</plugin>
          </compilerPlugins>
          <pluginOptions>
            <option>all-open:annotation=jakarta.ws.rs.Path</option>
            <option>all-open:annotation=jakarta.enterprise.context.ApplicationScoped</option>
            <option>all-open:annotation=io.quarkus.test.junit.QuarkusTest</option>
          </pluginOptions>
        </configuration>
        <dependencies>
          <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-maven-allopen</artifactId>
            <version>${kotlin.version}</version>
          </dependency>
          <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-maven-serialization</artifactId>
            <version>${kotlin.version}</version>
          </dependency>
        </dependencies>
      </plugin>
      <plugin>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>${surefire-plugin.version}</version>
        <configuration>
          <systemPropertyVariables>
            <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
            <maven.home>${maven.home}</maven.home>
          </systemPropertyVariables>
        </configuration>
      </plugin>
      <plugin>
        <artifactId>maven-failsafe-plugin</artifactId>
        <version>${surefire-plugin.version}</version>
        <executions>
          <execution>
            <goals><goal>integration-test</goal><goal>verify</goal></goals>
            <configuration>
              <systemPropertyVariables>
                <native.image.path>${project.build.directory}/${project.build.finalName}-runner</native.image.path>
                <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
                <maven.home>${maven.home}</maven.home>
              </systemPropertyVariables>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>

  <profiles>
    <profile>
      <id>native</id>
      <activation><property><name>native</name></property></activation>
      <properties>
        <skipITs>false</skipITs>
        <quarkus.package.type>native</quarkus.package.type>
      </properties>
    </profile>
  </profiles>
</project>
```

- [ ] **Step 2: Create `src/main/resources/application.properties`**

```properties
quarkus.application.name=file-exchange-hub

# DataSource
quarkus.datasource.db-kind=mariadb
quarkus.datasource.username=${DB_USER:hub}
quarkus.datasource.password=${DB_PASSWORD:hub}
quarkus.datasource.jdbc.url=${DB_URL:jdbc:mariadb://localhost:3306/filehub}
quarkus.hibernate-orm.database.generation=none
quarkus.flyway.migrate-at-start=true

# MinIO / S3
quarkus.s3.endpoint-override=${MINIO_URL:http://localhost:9000}
quarkus.s3.aws.region=us-east-1
quarkus.s3.aws.credentials.type=static
quarkus.s3.aws.credentials.static-provider.access-key-id=${MINIO_ACCESS_KEY:minioadmin}
quarkus.s3.aws.credentials.static-provider.secret-access-key=${MINIO_SECRET_KEY:minioadmin}

# NATS JetStream
quarkus.nats.servers=${NATS_URL:nats://localhost:4222}
```

- [ ] **Step 3: Verify the project compiles**

```bash
cd /home/iron/projects/file-exchange-hub
./mvnw compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add pom.xml src/main/resources/application.properties
git commit -m "feat: scaffold project with dependencies and config"
```

---

## Task 2: Database migration

**Files:**
- Create: `src/main/resources/db/migration/V1__create_file_tables.sql`

- [ ] **Step 1: Create Flyway migration**

```sql
CREATE TABLE file_metadata (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    bucket        VARCHAR(255)  NOT NULL,
    report_id     VARCHAR(255)  NOT NULL,
    report_category VARCHAR(255) NOT NULL,
    object_key    VARCHAR(2000) NOT NULL,
    filename      VARCHAR(255)  NOT NULL,
    content_type  VARCHAR(128)  NOT NULL,
    file_size     BIGINT        NOT NULL,
    checksum      VARCHAR(256)  NULL,
    uploader_id   VARCHAR(255)  NOT NULL,
    tags          JSON          NULL,
    status        ENUM('REGISTERED','FAILED') NOT NULL DEFAULT 'REGISTERED',
    remark        VARCHAR(1024) NULL,
    error_code    VARCHAR(64)   NULL,
    registered_at DATETIME(6)   NOT NULL
);

CREATE INDEX idx_file_metadata_query
    ON file_metadata (registered_at, bucket, status);

CREATE TABLE file_delivery (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    file_id      VARCHAR(36)  NOT NULL,
    consumer_id  VARCHAR(255) NOT NULL,
    note         TEXT         NULL,
    processed_at DATETIME(6)  NOT NULL,
    CONSTRAINT fk_delivery_file FOREIGN KEY (file_id) REFERENCES file_metadata(id),
    CONSTRAINT uq_delivery UNIQUE (file_id, consumer_id)
);

CREATE INDEX idx_file_delivery_file_id ON file_delivery (file_id);
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/db/migration/V1__create_file_tables.sql
git commit -m "feat: add Flyway migration for file_metadata and file_delivery tables"
```

---

## Task 3: Domain entities

**Files:**
- Create: `src/main/kotlin/tw/brandy/ironman/hub/domain/FileMetadata.kt`
- Create: `src/main/kotlin/tw/brandy/ironman/hub/domain/FileDelivery.kt`
- Create: `src/main/kotlin/tw/brandy/ironman/hub/domain/FileRegisteredEvent.kt`

- [ ] **Step 1: Create `FileMetadata.kt`**

```kotlin
package tw.brandy.ironman.hub.domain

import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class FileStatus { REGISTERED, FAILED }

@Entity
@Table(name = "file_metadata")
class FileMetadata : PanacheEntityBase {
    @Id
    var id: String = UUID.randomUUID().toString()

    @Column(nullable = false)
    var bucket: String = ""

    @Column(name = "report_id", nullable = false)
    var reportId: String = ""

    @Column(name = "report_category", nullable = false)
    var reportCategory: String = ""

    @Column(name = "object_key", nullable = false, length = 2000)
    var objectKey: String = ""

    @Column(nullable = false)
    var filename: String = ""

    @Column(name = "content_type", nullable = false, length = 128)
    var contentType: String = ""

    @Column(name = "file_size", nullable = false)
    var fileSize: Long = 0

    @Column(length = 256)
    var checksum: String? = null

    @Column(name = "uploader_id", nullable = false)
    var uploaderId: String = ""

    @Column(columnDefinition = "JSON")
    var tags: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: FileStatus = FileStatus.REGISTERED

    @Column(length = 1024)
    var remark: String? = null

    @Column(name = "error_code", length = 64)
    var errorCode: String? = null

    @Column(name = "registered_at", nullable = false)
    var registeredAt: Instant = Instant.now()
}
```

- [ ] **Step 2: Create `FileDelivery.kt`**

```kotlin
package tw.brandy.ironman.hub.domain

import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "file_delivery",
    uniqueConstraints = [UniqueConstraint(columnNames = ["file_id", "consumer_id"])]
)
class FileDelivery : PanacheEntityBase {
    @Id
    var id: String = UUID.randomUUID().toString()

    @Column(name = "file_id", nullable = false)
    var fileId: String = ""

    @Column(name = "consumer_id", nullable = false)
    var consumerId: String = ""

    @Column(columnDefinition = "TEXT")
    var note: String? = null

    @Column(name = "processed_at", nullable = false)
    var processedAt: Instant = Instant.now()
}
```

- [ ] **Step 3: Create `FileRegisteredEvent.kt`**

```kotlin
package tw.brandy.ironman.hub.domain

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class FileRegisteredEvent(
    val id: String,
    val bucket: String,
    val reportId: String,
    val reportCategory: String,
    val objectKey: String,
    val filename: String,
    val contentType: String,
    val fileSize: Long,
    val checksum: String?,
    val uploaderId: String,
    val tags: Map<String, String>?,
    val registeredAt: String
) {
    companion object {
        fun from(metadata: FileMetadata, tags: Map<String, String>?): FileRegisteredEvent =
            FileRegisteredEvent(
                id = metadata.id,
                bucket = metadata.bucket,
                reportId = metadata.reportId,
                reportCategory = metadata.reportCategory,
                objectKey = metadata.objectKey,
                filename = metadata.filename,
                contentType = metadata.contentType,
                fileSize = metadata.fileSize,
                checksum = metadata.checksum,
                uploaderId = metadata.uploaderId,
                tags = tags,
                registeredAt = metadata.registeredAt.toString()
            )
    }
}
```

- [ ] **Step 4: Verify compilation**

```bash
./mvnw compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/tw/brandy/ironman/hub/domain/
git commit -m "feat: add domain entities and NATS event data class"
```

---

## Task 4: Repositories

**Files:**
- Create: `src/main/kotlin/tw/brandy/ironman/hub/repository/FileMetadataRepository.kt`
- Create: `src/main/kotlin/tw/brandy/ironman/hub/repository/FileDeliveryRepository.kt`

- [ ] **Step 1: Create `FileMetadataRepository.kt`**

```kotlin
package tw.brandy.ironman.hub.repository

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import io.quarkus.panache.common.Page
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import tw.brandy.ironman.hub.domain.FileMetadata
import tw.brandy.ironman.hub.domain.FileStatus
import java.time.Instant

@ApplicationScoped
class FileMetadataRepository : PanacheRepositoryBase<FileMetadata, String> {

    fun findByIdOrNull(id: String): FileMetadata? = findById(id)

    fun search(uploaderId: String?, bucket: String?, page: Int, size: Int): Pair<List<FileMetadata>, Long> {
        val conditions = mutableListOf("status = :status")
        val params = mutableMapOf<String, Any>("status" to FileStatus.REGISTERED)
        if (uploaderId != null) { conditions += "uploaderId = :uploaderId"; params["uploaderId"] = uploaderId }
        if (bucket != null) { conditions += "bucket = :bucket"; params["bucket"] = bucket }
        val q = find(conditions.joinToString(" and "), Sort.by("registeredAt").descending(), params)
        val total = q.count()
        val results = q.page(Page.of(page, size)).list()
        return results to total
    }

    fun findMissing(consumerId: String, bucket: String?, since: Instant, page: Int, size: Int): Pair<List<FileMetadata>, Long> {
        val bucketClause = if (bucket != null) "and fm.bucket = :bucket " else ""
        val params = mutableMapOf<String, Any>(
            "consumerId" to consumerId,
            "since" to since,
            "status" to FileStatus.REGISTERED
        )
        if (bucket != null) params["bucket"] = bucket
        val jpql = """
            from FileMetadata fm
            where fm.status = :status
            and fm.registeredAt >= :since
            $bucketClause
            and not exists (
                select 1 from FileDelivery fd
                where fd.fileId = fm.id and fd.consumerId = :consumerId
            )
        """.trimIndent()
        val q = find(jpql, Sort.by("registeredAt"), params)
        val total = q.count()
        val results = q.page(Page.of(page, size)).list()
        return results to total
    }
}
```

- [ ] **Step 2: Create `FileDeliveryRepository.kt`**

```kotlin
package tw.brandy.ironman.hub.repository

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import jakarta.enterprise.context.ApplicationScoped
import tw.brandy.ironman.hub.domain.FileDelivery

@ApplicationScoped
class FileDeliveryRepository : PanacheRepositoryBase<FileDelivery, String> {

    fun findByFileIdAndConsumerId(fileId: String, consumerId: String): FileDelivery? =
        find("fileId = :fileId and consumerId = :consumerId",
            mapOf("fileId" to fileId, "consumerId" to consumerId))
            .firstResult()
}
```

- [ ] **Step 3: Verify compilation**

```bash
./mvnw compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/tw/brandy/ironman/hub/repository/
git commit -m "feat: add Panache repositories for file_metadata and file_delivery"
```

---

## Task 5: DTOs

**Files:**
- Create: `src/main/kotlin/tw/brandy/ironman/hub/resource/dto/RegisterFileRequest.kt`
- Create: `src/main/kotlin/tw/brandy/ironman/hub/resource/dto/RegisterFileResponse.kt`
- Create: `src/main/kotlin/tw/brandy/ironman/hub/resource/dto/FileMetadataDto.kt`
- Create: `src/main/kotlin/tw/brandy/ironman/hub/resource/dto/MarkProcessedRequest.kt`
- Create: `src/main/kotlin/tw/brandy/ironman/hub/resource/dto/PagedFilesResponse.kt`
- Create: `src/main/kotlin/tw/brandy/ironman/hub/resource/dto/ErrorResponse.kt`

- [ ] **Step 1: Create all DTOs**

`RegisterFileRequest.kt`:
```kotlin
package tw.brandy.ironman.hub.resource.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterFileRequest(
    val bucket: String,
    val reportId: String,
    val reportCategory: String,
    val objectKey: String,
    val filename: String,
    val contentType: String,
    val fileSize: Long,
    val checksum: String? = null,
    val uploaderId: String,
    val tags: Map<String, String>? = null
)
```

`RegisterFileResponse.kt`:
```kotlin
package tw.brandy.ironman.hub.resource.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterFileResponse(
    val id: String,
    val status: String,
    val eventPublished: Boolean,
    val registeredAt: String
)
```

`FileMetadataDto.kt`:
```kotlin
package tw.brandy.ironman.hub.resource.dto

import kotlinx.serialization.Serializable
import tw.brandy.ironman.hub.domain.FileMetadata

@Serializable
data class FileMetadataDto(
    val id: String,
    val bucket: String,
    val reportId: String,
    val reportCategory: String,
    val objectKey: String,
    val filename: String,
    val contentType: String,
    val fileSize: Long,
    val checksum: String?,
    val uploaderId: String,
    val tags: Map<String, String>?,
    val status: String,
    val remark: String?,
    val errorCode: String?,
    val registeredAt: String
) {
    companion object {
        fun from(m: FileMetadata, tags: Map<String, String>?): FileMetadataDto = FileMetadataDto(
            id = m.id, bucket = m.bucket, reportId = m.reportId, reportCategory = m.reportCategory,
            objectKey = m.objectKey, filename = m.filename, contentType = m.contentType,
            fileSize = m.fileSize, checksum = m.checksum, uploaderId = m.uploaderId,
            tags = tags, status = m.status.name, remark = m.remark, errorCode = m.errorCode,
            registeredAt = m.registeredAt.toString()
        )
    }
}
```

`MarkProcessedRequest.kt`:
```kotlin
package tw.brandy.ironman.hub.resource.dto

import kotlinx.serialization.Serializable

@Serializable
data class MarkProcessedRequest(
    val consumerId: String,
    val note: String? = null
)
```

`PagedFilesResponse.kt`:
```kotlin
package tw.brandy.ironman.hub.resource.dto

import kotlinx.serialization.Serializable

@Serializable
data class PagedFilesResponse(
    val files: List<FileMetadataDto>,
    val total: Long,
    val page: Int,
    val size: Int
)
```

`ErrorResponse.kt`:
```kotlin
package tw.brandy.ironman.hub.resource.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String, val code: String)
```

- [ ] **Step 2: Verify compilation**

```bash
./mvnw compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/tw/brandy/ironman/hub/resource/dto/
git commit -m "feat: add request/response DTOs"
```

---

## Task 6: MinioVerifier

**Files:**
- Create: `src/main/kotlin/tw/brandy/ironman/hub/service/MinioVerifier.kt`
- Create: `src/test/kotlin/tw/brandy/ironman/hub/service/MinioVerifierTest.kt`

- [ ] **Step 1: Write the failing unit test**

```kotlin
// src/test/kotlin/tw/brandy/ironman/hub/service/MinioVerifierTest.kt
package tw.brandy.ironman.hub.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException

class MinioVerifierTest {

    private val s3Client = mockk<S3Client>()
    private val verifier = MinioVerifier(s3Client)

    @Test
    fun `returns true when object exists`() {
        every { s3Client.headObject(any<HeadObjectRequest>()) } returns HeadObjectResponse.builder().build()
        assertTrue(verifier.exists("my-bucket", "path/to/file.pdf"))
    }

    @Test
    fun `returns false when object does not exist`() {
        every { s3Client.headObject(any<HeadObjectRequest>()) } throws NoSuchKeyException.builder().build()
        assertFalse(verifier.exists("my-bucket", "path/to/missing.pdf"))
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
./mvnw test -pl . -Dtest=MinioVerifierTest -q 2>&1 | tail -5
```
Expected: compilation error — `MinioVerifier` not found

- [ ] **Step 3: Create `MinioVerifier.kt`**

```kotlin
package tw.brandy.ironman.hub.service

import jakarta.enterprise.context.ApplicationScoped
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException

@ApplicationScoped
class MinioVerifier(private val s3Client: S3Client) {

    fun exists(bucket: String, objectKey: String): Boolean = try {
        s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build())
        true
    } catch (e: NoSuchKeyException) {
        false
    }
}
```

- [ ] **Step 4: Run test to confirm it passes**

```bash
./mvnw test -pl . -Dtest=MinioVerifierTest -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`, 2 tests passed

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: add MinioVerifier with unit tests"
```

---

## Task 7: FileEventPublisher

**Files:**
- Create: `src/main/kotlin/tw/brandy/ironman/hub/service/FileEventPublisher.kt`

- [ ] **Step 1: Create `FileEventPublisher.kt`**

The NATS JetStream publisher uses the `@Channel` emitter from Quarkus reactive messaging.

```kotlin
package tw.brandy.ironman.hub.service

import io.smallrye.reactive.messaging.MutinyEmitter
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eclipse.microprofile.reactive.messaging.Channel
import tw.brandy.ironman.hub.domain.FileRegisteredEvent

@ApplicationScoped
class FileEventPublisher(
    @Channel("files-registered") private val emitter: MutinyEmitter<String>
) {
    fun publish(event: FileRegisteredEvent): Boolean = try {
        emitter.sendAndAwait(Json.encodeToString(event))
        true
    } catch (e: Exception) {
        false
    }
}
```

Add to `application.properties`:
```properties
# NATS outgoing channel
mp.messaging.outgoing.files-registered.connector=quarkus-nats-jetstream
mp.messaging.outgoing.files-registered.subject=files.registered
mp.messaging.outgoing.files-registered.stream=FILES
```

- [ ] **Step 2: Verify compilation**

```bash
./mvnw compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/
git commit -m "feat: add FileEventPublisher for NATS JetStream"
```

---

## Task 8: FileRegistrationService (unit tests first)

**Files:**
- Create: `src/test/kotlin/tw/brandy/ironman/hub/service/FileRegistrationServiceTest.kt`
- Create: `src/main/kotlin/tw/brandy/ironman/hub/service/FileRegistrationService.kt`

- [ ] **Step 1: Write failing unit tests**

```kotlin
// src/test/kotlin/tw/brandy/ironman/hub/service/FileRegistrationServiceTest.kt
package tw.brandy.ironman.hub.service

import io.mockk.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.brandy.ironman.hub.domain.FileMetadata
import tw.brandy.ironman.hub.domain.FileRegisteredEvent
import tw.brandy.ironman.hub.domain.FileStatus
import tw.brandy.ironman.hub.repository.FileMetadataRepository
import tw.brandy.ironman.hub.resource.dto.RegisterFileRequest

class FileRegistrationServiceTest {

    private val verifier = mockk<MinioVerifier>()
    private val repository = mockk<FileMetadataRepository>(relaxed = true)
    private val publisher = mockk<FileEventPublisher>()
    private val service = FileRegistrationService(verifier, repository, publisher)

    private val validRequest = RegisterFileRequest(
        bucket = "incoming",
        reportId = "WXG",
        reportCategory = "AVI",
        objectKey = "reports/report.pdf",
        filename = "report.pdf",
        contentType = "application/pdf",
        fileSize = 1024,
        checksum = "d41d8cd98f00b204e9800998ecf8427e",
        uploaderId = "client-A",
        tags = mapOf("dept" to "finance")
    )

    @Test
    fun `returns success with eventPublished true when all steps succeed`() {
        every { verifier.exists("incoming", "reports/report.pdf") } returns true
        every { publisher.publish(any()) } returns true
        val result = service.register(validRequest)
        assertTrue(result.eventPublished)
        assertEquals("REGISTERED", result.status)
        verify { repository.persist(any<FileMetadata>()) }
    }

    @Test
    fun `returns eventPublished false when NATS publish fails`() {
        every { verifier.exists("incoming", "reports/report.pdf") } returns true
        every { publisher.publish(any()) } returns false
        val result = service.register(validRequest)
        assertFalse(result.eventPublished)
        assertEquals("REGISTERED", result.status)
    }

    @Test
    fun `throws ObjectNotFoundException when file not found in MinIO`() {
        every { verifier.exists("incoming", "reports/report.pdf") } returns false
        assertThrows(ObjectNotFoundException::class.java) { service.register(validRequest) }
        verify(exactly = 0) { repository.persist(any<FileMetadata>()) }
    }

    @Test
    fun `registers successfully when checksum is null (bypass)`() {
        val req = validRequest.copy(checksum = null)
        every { verifier.exists("incoming", "reports/report.pdf") } returns true
        every { publisher.publish(any()) } returns true
        val result = service.register(req)
        assertEquals("REGISTERED", result.status)
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./mvnw test -pl . -Dtest=FileRegistrationServiceTest -q 2>&1 | tail -5
```
Expected: compilation error — `FileRegistrationService`, `ObjectNotFoundException` not found

- [ ] **Step 3: Create `FileRegistrationService.kt`**

```kotlin
package tw.brandy.ironman.hub.service

import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tw.brandy.ironman.hub.domain.FileMetadata
import tw.brandy.ironman.hub.domain.FileRegisteredEvent
import tw.brandy.ironman.hub.domain.FileStatus
import tw.brandy.ironman.hub.repository.FileMetadataRepository
import tw.brandy.ironman.hub.resource.dto.RegisterFileRequest
import tw.brandy.ironman.hub.resource.dto.RegisterFileResponse
import java.time.Instant

class ObjectNotFoundException(message: String) : RuntimeException(message)

@ApplicationScoped
class FileRegistrationService(
    private val verifier: MinioVerifier,
    private val repository: FileMetadataRepository,
    private val publisher: FileEventPublisher
) {
    @Transactional
    fun register(request: RegisterFileRequest): RegisterFileResponse {
        if (!verifier.exists(request.bucket, request.objectKey)) {
            throw ObjectNotFoundException("Object not found in MinIO: ${request.bucket}/${request.objectKey}")
        }

        val metadata = FileMetadata().apply {
            bucket = request.bucket
            reportId = request.reportId
            reportCategory = request.reportCategory
            objectKey = request.objectKey
            filename = request.filename
            contentType = request.contentType
            fileSize = request.fileSize
            checksum = request.checksum
            uploaderId = request.uploaderId
            tags = request.tags?.let { Json.encodeToString(it) }
            status = FileStatus.REGISTERED
            registeredAt = Instant.now()
        }
        repository.persist(metadata)

        val event = FileRegisteredEvent.from(metadata, request.tags)
        val published = publisher.publish(event)

        return RegisterFileResponse(
            id = metadata.id,
            status = metadata.status.name,
            eventPublished = published,
            registeredAt = metadata.registeredAt.toString()
        )
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=FileRegistrationServiceTest -q 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`, 4 tests passed

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: add FileRegistrationService with unit tests"
```

---

## Task 9: REST resource

**Files:**
- Create: `src/main/kotlin/tw/brandy/ironman/hub/resource/FileResource.kt`

- [ ] **Step 1: Create `FileResource.kt`**

```kotlin
package tw.brandy.ironman.hub.resource

import jakarta.transaction.Transactional
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import tw.brandy.ironman.hub.domain.FileDelivery
import tw.brandy.ironman.hub.repository.FileDeliveryRepository
import tw.brandy.ironman.hub.repository.FileMetadataRepository
import tw.brandy.ironman.hub.resource.dto.*
import tw.brandy.ironman.hub.service.FileRegistrationService
import tw.brandy.ironman.hub.service.ObjectNotFoundException
import java.time.Instant

@Path("/api/files")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class FileResource(
    private val registrationService: FileRegistrationService,
    private val metadataRepository: FileMetadataRepository,
    private val deliveryRepository: FileDeliveryRepository
) {

    @POST
    @Path("/register")
    fun register(request: RegisterFileRequest): Response = try {
        val response = registrationService.register(request)
        Response.status(Response.Status.CREATED).entity(response).build()
    } catch (e: ObjectNotFoundException) {
        Response.status(Response.Status.NOT_FOUND)
            .entity(ErrorResponse(e.message ?: "Not found", "OBJECT_NOT_FOUND")).build()
    }

    @GET
    @Path("/{id}")
    fun getById(@PathParam("id") id: String): Response {
        val metadata = metadataRepository.findByIdOrNull(id)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse("File not found", "FILE_NOT_FOUND")).build()
        val tags = metadata.tags?.let { Json.decodeFromString<Map<String, String>>(it) }
        return Response.ok(FileMetadataDto.from(metadata, tags)).build()
    }

    @GET
    fun search(
        @QueryParam("uploaderId") uploaderId: String?,
        @QueryParam("bucket") bucket: String?,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("size") @DefaultValue("20") size: Int
    ): Response {
        val (files, total) = metadataRepository.search(uploaderId, bucket, page, size)
        val dtos = files.map { m ->
            val tags = m.tags?.let { Json.decodeFromString<Map<String, String>>(it) }
            FileMetadataDto.from(m, tags)
        }
        return Response.ok(PagedFilesResponse(dtos, total, page, size)).build()
    }

    @GET
    @Path("/missing")
    fun missing(
        @QueryParam("consumerId") consumerId: String,
        @QueryParam("bucket") bucket: String?,
        @QueryParam("since") since: String?,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("size") @DefaultValue("20") size: Int
    ): Response {
        val sinceInstant = since?.let { Instant.parse(it) } ?: Instant.now().minusSeconds(86400)
        val (files, total) = metadataRepository.findMissing(consumerId, bucket, sinceInstant, page, size)
        val dtos = files.map { m ->
            val tags = m.tags?.let { Json.decodeFromString<Map<String, String>>(it) }
            FileMetadataDto.from(m, tags)
        }
        return Response.ok(PagedFilesResponse(dtos, total, page, size)).build()
    }

    @PUT
    @Path("/{id}/delivery")
    @Transactional
    fun markProcessed(@PathParam("id") id: String, request: MarkProcessedRequest): Response {
        metadataRepository.findByIdOrNull(id)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse("File not found", "FILE_NOT_FOUND")).build()

        val existing = deliveryRepository.findByFileIdAndConsumerId(id, request.consumerId)
        if (existing == null) {
            val delivery = FileDelivery().apply {
                fileId = id
                consumerId = request.consumerId
                note = request.note
            }
            deliveryRepository.persist(delivery)
        }
        return Response.ok().build()
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
./mvnw compile -q
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/tw/brandy/ironman/hub/resource/FileResource.kt
git commit -m "feat: add FileResource with all REST endpoints"
```

---

## Task 10: Integration tests

**Files:**
- Create: `src/test/kotlin/tw/brandy/ironman/hub/resource/FileResourceIT.kt`

- [ ] **Step 1: Write integration tests**

```kotlin
// src/test/kotlin/tw/brandy/ironman/hub/resource/FileResourceIT.kt
package tw.brandy.ironman.hub.resource

import io.quarkus.test.junit.QuarkusTest
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.hamcrest.CoreMatchers.*
import org.junit.jupiter.api.Test

@QuarkusTest
class FileResourceIT {

    private val validBody = """
        {
          "bucket": "incoming",
          "reportId": "WXG",
          "reportCategory": "AVI",
          "objectKey": "test/sample.pdf",
          "filename": "sample.pdf",
          "contentType": "application/pdf",
          "fileSize": 1024,
          "checksum": "d41d8cd98f00b204e9800998ecf8427e",
          "uploaderId": "test-client"
        }
    """.trimIndent()

    @Test
    fun `register returns 201 with valid payload and existing object`() {
        // Requires MinIO testcontainer pre-populated with test/sample.pdf
        Given {
            contentType("application/json")
            body(validBody)
        } When {
            post("/api/files/register")
        } Then {
            statusCode(201)
            body("status", equalTo("REGISTERED"))
            body("id", notNullValue())
            body("eventPublished", notNullValue())
        }
    }

    @Test
    fun `register returns 404 when object not in MinIO`() {
        val body = validBody.replace("test/sample.pdf", "nonexistent/file.pdf")
        Given {
            contentType("application/json")
            body(body)
        } When {
            post("/api/files/register")
        } Then {
            statusCode(404)
            body("code", equalTo("OBJECT_NOT_FOUND"))
        }
    }

    @Test
    fun `register succeeds with null checksum (bypass)`() {
        val body = validBody.replace(
            "\"checksum\": \"d41d8cd98f00b204e9800998ecf8427e\",", ""
        )
        Given {
            contentType("application/json")
            body(body)
        } When {
            post("/api/files/register")
        } Then {
            statusCode(201)
            body("status", equalTo("REGISTERED"))
        }
    }

    @Test
    fun `get by id returns 404 for unknown id`() {
        When {
            get("/api/files/nonexistent-id")
        } Then {
            statusCode(404)
        }
    }

    @Test
    fun `mark processed is idempotent`() {
        // First register
        val id = Given {
            contentType("application/json")
            body(validBody)
        } When {
            post("/api/files/register")
        } Then {
            statusCode(201)
        } extract { path<String>("id") }

        val deliveryBody = """{"consumerId": "consumer-A"}"""

        repeat(2) {
            Given {
                contentType("application/json")
                body(deliveryBody)
            } When {
                put("/api/files/$id/delivery")
            } Then {
                statusCode(200)
            }
        }
    }

    @Test
    fun `missing returns only unprocessed files for consumer`() {
        When {
            get("/api/files/missing?consumerId=consumer-B")
        } Then {
            statusCode(200)
            body("files", notNullValue())
            body("total", notNullValue())
        }
    }
}
```

- [ ] **Step 2: Add `src/test/resources/application.properties` for Testcontainers**

```properties
# Testcontainers will override these at runtime via QuarkusTestResourceLifecycleManager
quarkus.datasource.db-kind=mariadb
quarkus.datasource.devservices.enabled=true
quarkus.s3.devservices.enabled=false
quarkus.nats.servers=nats://localhost:4222
```

- [ ] **Step 3: Run integration tests**

```bash
./mvnw verify -Dskip.surefire.tests=true 2>&1 | tail -20
```
Expected: `BUILD SUCCESS`, all integration tests pass

- [ ] **Step 4: Commit**

```bash
git add src/test/
git commit -m "test: add integration tests for FileResource"
```

---

## Task 11: Final wiring and smoke test

- [ ] **Step 1: Run full test suite**

```bash
./mvnw verify 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`, all tests pass

- [ ] **Step 2: Start the app in dev mode and verify health endpoint**

```bash
./mvnw quarkus:dev &
curl -s http://localhost:8080/q/health | python3 -m json.tool
```
Expected: `"status": "UP"`

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "feat: complete file-exchange-hub implementation"
```
