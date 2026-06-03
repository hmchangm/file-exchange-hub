# Status Query GUI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone Kotlin/Ktor web app (`status-gui/`) that gives ops/support a browser UI to search files, view file detail with delivery records, and find files a consumer hasn't processed — reading directly from MariaDB with a read-only user.

**Architecture:** A separate Maven project under `status-gui/` with no parent dependency on the Quarkus app. Ktor (Netty) serves three server-side-rendered HTML pages via `kotlinx.html`. Exposed (JDBC) connects to MariaDB through a HikariCP pool configured via env vars.

**Tech Stack:** Kotlin 2.2.10, Ktor 3.0.3, Exposed 0.55.0, HikariCP 5.1.0, MariaDB Connector/J 3.4.1, kotlinx.html (bundled with Ktor), JUnit 5, Testcontainers 1.19.7

---

## File Map

| File | Responsibility |
|---|---|
| `status-gui/pom.xml` | Standalone Maven build — Ktor, Exposed, HikariCP, MariaDB |
| `Main.kt` | `embeddedServer(Netty)` entry point, wires routing |
| `Database.kt` | HikariCP pool; reads `DB_*` env vars; read-only flag |
| `repository/FileQueryRepository.kt` | Interface + `PagedResult` + `FileRow` + `DeliveryRow` model classes |
| `repository/ExposedFileQueryRepository.kt` | Exposed table objects + all SQL queries |
| `templates/Layout.kt` | Shared HTML shell: `<head>`, nav bar, CSS |
| `templates/FileSearchPage.kt` | Search form + paginated results table |
| `templates/FileDetailPage.kt` | Metadata grid + delivery records table |
| `templates/MissingFilesPage.kt` | Consumer filter form + missing files table |
| `routing/FileSearchRoutes.kt` | `GET /files` and `GET /files/{id}` |
| `routing/MissingFilesRoutes.kt` | `GET /missing` |
| `test/.../FakeFileQueryRepository.kt` | Hand-written fake for route tests |
| `test/.../ExposedFileQueryRepositoryTest.kt` | Integration tests with Testcontainers MariaDB |
| `test/.../FileSearchRoutesTest.kt` | Route tests using `testApplication` + fake repo |
| `test/.../MissingFilesRoutesTest.kt` | Route tests using `testApplication` + fake repo |

---

## Task 1: Project Scaffold

**Files:**
- Create: `status-gui/pom.xml`
- Create: `status-gui/src/main/kotlin/mlid/enghub/statusgui/Main.kt`

- [ ] **Step 1: Create directory structure**

```bash
mkdir -p status-gui/src/main/kotlin/mlid/enghub/statusgui
mkdir -p status-gui/src/test/kotlin/mlid/enghub/statusgui
mkdir -p status-gui/src/main/resources
```

- [ ] **Step 2: Create `status-gui/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>mlid.enghub</groupId>
  <artifactId>status-gui</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>jar</packaging>

  <properties>
    <kotlin.version>2.2.10</kotlin.version>
    <ktor.version>3.0.3</ktor.version>
    <exposed.version>0.55.0</exposed.version>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <main.class>mlid.enghub.statusgui.MainKt</main.class>
  </properties>

  <dependencies>
    <!-- Ktor -->
    <dependency><groupId>io.ktor</groupId><artifactId>ktor-server-netty-jvm</artifactId><version>${ktor.version}</version></dependency>
    <dependency><groupId>io.ktor</groupId><artifactId>ktor-server-html-builder-jvm</artifactId><version>${ktor.version}</version></dependency>
    <dependency><groupId>io.ktor</groupId><artifactId>ktor-server-status-pages-jvm</artifactId><version>${ktor.version}</version></dependency>
    <!-- Exposed + MariaDB -->
    <dependency><groupId>org.jetbrains.exposed</groupId><artifactId>exposed-core</artifactId><version>${exposed.version}</version></dependency>
    <dependency><groupId>org.jetbrains.exposed</groupId><artifactId>exposed-jdbc</artifactId><version>${exposed.version}</version></dependency>
    <dependency><groupId>org.jetbrains.exposed</groupId><artifactId>exposed-java-time</artifactId><version>${exposed.version}</version></dependency>
    <dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId><version>5.1.0</version></dependency>
    <dependency><groupId>org.mariadb.jdbc</groupId><artifactId>mariadb-java-client</artifactId><version>3.4.1</version></dependency>
    <!-- Kotlin -->
    <dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-stdlib</artifactId><version>${kotlin.version}</version></dependency>
    <!-- Logging -->
    <dependency><groupId>ch.qos.logback</groupId><artifactId>logback-classic</artifactId><version>1.5.12</version></dependency>
    <!-- Test -->
    <dependency><groupId>io.ktor</groupId><artifactId>ktor-server-test-host-jvm</artifactId><version>${ktor.version}</version><scope>test</scope></dependency>
    <dependency><groupId>org.jetbrains.kotlin</groupId><artifactId>kotlin-test-junit5</artifactId><version>${kotlin.version}</version><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>mariadb</artifactId><version>1.19.7</version><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><version>1.19.7</version><scope>test</scope></dependency>
  </dependencies>

  <build>
    <sourceDirectory>src/main/kotlin</sourceDirectory>
    <testSourceDirectory>src/test/kotlin</testSourceDirectory>
    <plugins>
      <plugin>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>kotlin-maven-plugin</artifactId>
        <version>${kotlin.version}</version>
        <executions>
          <execution><id>compile</id><goals><goal>compile</goal></goals></execution>
          <execution><id>test-compile</id><goals><goal>test-compile</goal></goals></execution>
        </executions>
        <configuration>
          <jvmTarget>17</jvmTarget>
        </configuration>
      </plugin>
      <plugin>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.2.5</version>
      </plugin>
      <plugin>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.6.0</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
          </execution>
        </executions>
        <configuration>
          <transformers>
            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
              <mainClass>${main.class}</mainClass>
            </transformer>
            <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
          </transformers>
          <filters>
            <filter>
              <artifact>*:*</artifact>
              <excludes><exclude>META-INF/*.SF</exclude><exclude>META-INF/*.DSA</exclude><exclude>META-INF/*.RSA</exclude></excludes>
            </filter>
          </filters>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 3: Create `Main.kt` stub**

```kotlin
package mlid.enghub.statusgui

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val port = System.getenv("GUI_PORT")?.toInt() ?: 8090
    embeddedServer(Netty, port = port) {
    }.start(wait = true)
}
```

- [ ] **Step 4: Compile to verify scaffold**

```bash
cd status-gui && mvn compile -q
```

Expected: `BUILD SUCCESS` with no errors.

- [ ] **Step 5: Commit**

```bash
git add status-gui/
git commit -m "feat(status-gui): scaffold Ktor project with pom.xml and empty main"
```

---

## Task 2: Database Module and Model Classes

**Files:**
- Create: `status-gui/src/main/kotlin/mlid/enghub/statusgui/Database.kt`
- Create: `status-gui/src/main/kotlin/mlid/enghub/statusgui/repository/FileQueryRepository.kt`

- [ ] **Step 1: Create `Database.kt`**

```kotlin
package mlid.enghub.statusgui

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

fun connectDatabase(): Database {
    val config = HikariConfig().apply {
        jdbcUrl = "jdbc:mariadb://${env("DB_HOST")}:${env("DB_PORT", "3306")}/${env("DB_NAME")}"
        username = env("DB_USER")
        password = env("DB_PASS")
        maximumPoolSize = 5
        isReadOnly = true
        poolName = "status-gui"
    }
    return Database.connect(HikariDataSource(config))
}

internal fun env(name: String, default: String? = null): String =
    System.getenv(name) ?: default ?: error("Missing required env var: $name")
```

- [ ] **Step 2: Create `repository/FileQueryRepository.kt`** — interface + read-only model classes

```kotlin
package mlid.enghub.statusgui.repository

import java.time.LocalDateTime

data class FileRow(
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
    val tags: String?,
    val status: String,
    val remark: String?,
    val errorCode: String?,
    val registeredAt: LocalDateTime,
)

data class DeliveryRow(
    val id: String,
    val fileId: String,
    val consumerId: String,
    val note: String?,
    val processedAt: LocalDateTime,
)

data class PagedResult(val rows: List<FileRow>, val total: Long)

interface FileQueryRepository {
    fun search(
        uploaderId: String?,
        bucket: String?,
        status: String?,
        since: LocalDateTime?,
        page: Int,
        size: Int = 20,
    ): PagedResult

    fun findById(id: String): FileRow?

    fun findDeliveries(fileId: String): List<DeliveryRow>

    fun findMissing(
        consumerId: String,
        bucket: String?,
        since: LocalDateTime,
        page: Int,
        size: Int = 20,
    ): PagedResult
}
```

- [ ] **Step 3: Compile to verify**

```bash
cd status-gui && mvn compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add status-gui/src/main/kotlin/mlid/enghub/statusgui/Database.kt \
        status-gui/src/main/kotlin/mlid/enghub/statusgui/repository/FileQueryRepository.kt
git commit -m "feat(status-gui): add Database connection and FileQueryRepository interface"
```

---

## Task 3: ExposedFileQueryRepository with Integration Tests

**Files:**
- Create: `status-gui/src/main/kotlin/mlid/enghub/statusgui/repository/ExposedFileQueryRepository.kt`
- Create: `status-gui/src/test/kotlin/mlid/enghub/statusgui/repository/ExposedFileQueryRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

Create `status-gui/src/test/kotlin/mlid/enghub/statusgui/repository/ExposedFileQueryRepositoryTest.kt`:

```kotlin
package mlid.enghub.statusgui.repository

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.MariaDBContainer
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExposedFileQueryRepositoryTest {

    private lateinit var container: MariaDBContainer<*>
    private lateinit var repo: ExposedFileQueryRepository

    @BeforeAll
    fun setup() {
        container = MariaDBContainer("mariadb:10.11")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
        container.start()

        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = container.jdbcUrl
            username = container.username
            password = container.password
        })
        Database.connect(ds)

        transaction {
            exec("""
                CREATE TABLE file_metadata (
                    id VARCHAR(36) NOT NULL PRIMARY KEY,
                    bucket VARCHAR(255) NOT NULL,
                    report_id VARCHAR(255) NOT NULL,
                    report_category VARCHAR(255) NOT NULL,
                    object_key VARCHAR(2000) NOT NULL,
                    filename VARCHAR(255) NOT NULL,
                    content_type VARCHAR(128) NOT NULL,
                    file_size BIGINT NOT NULL,
                    checksum VARCHAR(256) NULL,
                    uploader_id VARCHAR(255) NOT NULL,
                    tags TEXT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
                    remark VARCHAR(1024) NULL,
                    error_code VARCHAR(64) NULL,
                    registered_at DATETIME(6) NOT NULL
                )
            """.trimIndent())
            exec("""
                CREATE TABLE file_delivery (
                    id VARCHAR(36) NOT NULL PRIMARY KEY,
                    file_id VARCHAR(36) NOT NULL,
                    consumer_id VARCHAR(255) NOT NULL,
                    note TEXT NULL,
                    processed_at DATETIME(6) NOT NULL,
                    CONSTRAINT fk_delivery_file FOREIGN KEY (file_id) REFERENCES file_metadata(id),
                    CONSTRAINT uq_delivery UNIQUE (file_id, consumer_id)
                )
            """.trimIndent())
        }

        repo = ExposedFileQueryRepository()
    }

    @AfterAll
    fun teardown() { container.stop() }

    private fun insertFile(
        id: String = UUID.randomUUID().toString(),
        bucket: String = "test-bucket",
        uploaderId: String = "uploader-01",
        status: String = "REGISTERED",
        registeredAt: LocalDateTime = LocalDateTime.now(),
    ): String {
        transaction {
            exec("""
                INSERT INTO file_metadata
                    (id, bucket, report_id, report_category, object_key, filename, content_type, file_size, uploader_id, status, registered_at)
                VALUES
                    ('$id', '$bucket', 'r1', 'cat1', 'key/$id', 'file-$id.csv', 'text/csv', 1024, '$uploaderId', '$status', '$registeredAt')
            """.trimIndent())
        }
        return id
    }

    private fun insertDelivery(fileId: String, consumerId: String): String {
        val id = UUID.randomUUID().toString()
        transaction {
            exec("""
                INSERT INTO file_delivery (id, file_id, consumer_id, processed_at)
                VALUES ('$id', '$fileId', '$consumerId', '${LocalDateTime.now()}')
            """.trimIndent())
        }
        return id
    }

    @Test
    fun `search returns matching rows`() {
        val id = insertFile(bucket = "finance", uploaderId = "up-search-test")
        val result = repo.search(uploaderId = "up-search-test", bucket = null, status = null, since = null, page = 0)
        assertEquals(1, result.rows.size)
        assertEquals(id, result.rows[0].id)
    }

    @Test
    fun `search filters by bucket`() {
        insertFile(bucket = "bucket-a")
        insertFile(bucket = "bucket-b")
        val result = repo.search(uploaderId = null, bucket = "bucket-a", status = null, since = null, page = 0)
        assert(result.rows.all { it.bucket == "bucket-a" })
    }

    @Test
    fun `search filters by status`() {
        insertFile(status = "FAILED", uploaderId = "up-status-test")
        insertFile(status = "REGISTERED", uploaderId = "up-status-test")
        val failed = repo.search(uploaderId = "up-status-test", bucket = null, status = "FAILED", since = null, page = 0)
        assertEquals(1, failed.rows.size)
        assertEquals("FAILED", failed.rows[0].status)
    }

    @Test
    fun `search total reflects full count not page size`() {
        val uploader = "up-total-${UUID.randomUUID()}"
        repeat(5) { insertFile(uploaderId = uploader) }
        val result = repo.search(uploaderId = uploader, bucket = null, status = null, since = null, page = 0, size = 2)
        assertEquals(2, result.rows.size)
        assertEquals(5, result.total)
    }

    @Test
    fun `findById returns file when exists`() {
        val id = insertFile()
        val row = repo.findById(id)
        assertNotNull(row)
        assertEquals(id, row.id)
    }

    @Test
    fun `findById returns null for unknown id`() {
        assertNull(repo.findById("no-such-id"))
    }

    @Test
    fun `findDeliveries returns delivery records for file`() {
        val fileId = insertFile()
        insertDelivery(fileId, "consumer-a")
        insertDelivery(fileId, "consumer-b")
        val deliveries = repo.findDeliveries(fileId)
        assertEquals(2, deliveries.size)
        assert(deliveries.any { it.consumerId == "consumer-a" })
        assert(deliveries.any { it.consumerId == "consumer-b" })
    }

    @Test
    fun `findMissing returns files not delivered to consumer`() {
        val consumer = "consumer-missing-${UUID.randomUUID()}"
        val deliveredId = insertFile()
        val missingId = insertFile()
        insertDelivery(deliveredId, consumer)
        val since = LocalDateTime.now().minusDays(1)
        val result = repo.findMissing(consumer, bucket = null, since = since, page = 0)
        val ids = result.rows.map { it.id }
        assert(missingId in ids)
        assert(deliveredId !in ids)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd status-gui && mvn test -Dtest=ExposedFileQueryRepositoryTest -q 2>&1 | tail -5
```

Expected: compilation error — `ExposedFileQueryRepository` does not exist yet.

- [ ] **Step 3: Create `repository/ExposedFileQueryRepository.kt`**

```kotlin
package mlid.enghub.statusgui.repository

import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

internal object FileMetadataTable : Table("file_metadata") {
    val id = varchar("id", 36)
    val bucket = varchar("bucket", 255)
    val reportId = varchar("report_id", 255)
    val reportCategory = varchar("report_category", 255)
    val objectKey = varchar("object_key", 2000)
    val filename = varchar("filename", 255)
    val contentType = varchar("content_type", 128)
    val fileSize = long("file_size")
    val checksum = varchar("checksum", 256).nullable()
    val uploaderId = varchar("uploader_id", 255)
    val tags = text("tags").nullable()
    val status = varchar("status", 20)
    val remark = varchar("remark", 1024).nullable()
    val errorCode = varchar("error_code", 64).nullable()
    val registeredAt = datetime("registered_at")
    override val primaryKey = PrimaryKey(id)
}

internal object FileDeliveryTable : Table("file_delivery") {
    val id = varchar("id", 36)
    val fileId = varchar("file_id", 36)
    val consumerId = varchar("consumer_id", 255)
    val note = text("note").nullable()
    val processedAt = datetime("processed_at")
    override val primaryKey = PrimaryKey(id)
}

class ExposedFileQueryRepository : FileQueryRepository {

    override fun search(
        uploaderId: String?,
        bucket: String?,
        status: String?,
        since: LocalDateTime?,
        page: Int,
        size: Int,
    ): PagedResult = transaction {
        var query = FileMetadataTable.selectAll()
        if (uploaderId != null) query = query.andWhere { FileMetadataTable.uploaderId eq uploaderId }
        if (bucket != null) query = query.andWhere { FileMetadataTable.bucket eq bucket }
        if (status != null) query = query.andWhere { FileMetadataTable.status eq status }
        if (since != null) query = query.andWhere { FileMetadataTable.registeredAt greaterEq since }
        val total = query.count()
        val rows = query
            .orderBy(FileMetadataTable.registeredAt to SortOrder.DESC)
            .limit(size, offset = (page * size).toLong())
            .map { it.toFileRow() }
        PagedResult(rows, total)
    }

    override fun findById(id: String): FileRow? = transaction {
        FileMetadataTable.selectAll()
            .where { FileMetadataTable.id eq id }
            .singleOrNull()
            ?.toFileRow()
    }

    override fun findDeliveries(fileId: String): List<DeliveryRow> = transaction {
        FileDeliveryTable.selectAll()
            .where { FileDeliveryTable.fileId eq fileId }
            .map { it.toDeliveryRow() }
    }

    override fun findMissing(
        consumerId: String,
        bucket: String?,
        since: LocalDateTime,
        page: Int,
        size: Int,
    ): PagedResult = transaction {
        var query = FileMetadataTable
            .join(
                FileDeliveryTable,
                JoinType.LEFT,
                onColumn = FileMetadataTable.id,
                otherColumn = FileDeliveryTable.fileId,
                additionalConstraint = { FileDeliveryTable.consumerId eq consumerId },
            )
            .selectAll()
            .where { FileDeliveryTable.id.isNull() }
            .andWhere { FileMetadataTable.registeredAt greaterEq since }
        if (bucket != null) query = query.andWhere { FileMetadataTable.bucket eq bucket }
        val total = query.count()
        val rows = query
            .orderBy(FileMetadataTable.registeredAt to SortOrder.DESC)
            .limit(size, offset = (page * size).toLong())
            .map { it.toFileRow() }
        PagedResult(rows, total)
    }

    private fun ResultRow.toFileRow() = FileRow(
        id = this[FileMetadataTable.id],
        bucket = this[FileMetadataTable.bucket],
        reportId = this[FileMetadataTable.reportId],
        reportCategory = this[FileMetadataTable.reportCategory],
        objectKey = this[FileMetadataTable.objectKey],
        filename = this[FileMetadataTable.filename],
        contentType = this[FileMetadataTable.contentType],
        fileSize = this[FileMetadataTable.fileSize],
        checksum = this[FileMetadataTable.checksum],
        uploaderId = this[FileMetadataTable.uploaderId],
        tags = this[FileMetadataTable.tags],
        status = this[FileMetadataTable.status],
        remark = this[FileMetadataTable.remark],
        errorCode = this[FileMetadataTable.errorCode],
        registeredAt = this[FileMetadataTable.registeredAt],
    )

    private fun ResultRow.toDeliveryRow() = DeliveryRow(
        id = this[FileDeliveryTable.id],
        fileId = this[FileDeliveryTable.fileId],
        consumerId = this[FileDeliveryTable.consumerId],
        note = this[FileDeliveryTable.note],
        processedAt = this[FileDeliveryTable.processedAt],
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd status-gui && mvn test -Dtest=ExposedFileQueryRepositoryTest
```

Expected: `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```bash
git add status-gui/src/
git commit -m "feat(status-gui): add ExposedFileQueryRepository with integration tests"
```

---

## Task 4: Layout Template

**Files:**
- Create: `status-gui/src/main/kotlin/mlid/enghub/statusgui/templates/Layout.kt`

No tests — layout is pure rendering code verified visually in later tasks.

- [ ] **Step 1: Create `templates/Layout.kt`**

```kotlin
package mlid.enghub.statusgui.templates

import kotlinx.html.BODY
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe

fun layout(title: String, activeTab: String, block: BODY.() -> Unit): String =
    createHTML().html {
        head {
            meta(charset = "UTF-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            title("File Hub — $title")
            style {
                unsafe {
                    raw(
                        """
                        body{font-family:sans-serif;margin:0;color:#333}
                        nav{background:#1565c0;padding:10px 20px;display:flex;gap:24px}
                        nav a{color:white;text-decoration:none;padding-bottom:2px}
                        nav a.active{border-bottom:2px solid white}
                        .container{max-width:1200px;margin:0 auto;padding:20px}
                        .filter-bar{background:#f5f5f5;padding:12px 16px;display:flex;gap:12px;flex-wrap:wrap;align-items:flex-end;margin-bottom:16px;border-radius:4px}
                        .filter-bar label{display:flex;flex-direction:column;font-size:.75rem;font-weight:bold;text-transform:uppercase;color:#666;gap:4px}
                        input,select{padding:5px 8px;border:1px solid #ccc;border-radius:3px;font-size:.9rem}
                        button{padding:6px 16px;background:#1565c0;color:white;border:none;border-radius:3px;cursor:pointer;font-size:.9rem}
                        table{width:100%;border-collapse:collapse}
                        th{background:#e3f2fd;text-align:left;padding:8px 12px;font-size:.85rem}
                        td{padding:7px 12px;font-size:.85rem;border-top:1px solid #eee}
                        tr:nth-child(even) td{background:#fafafa}
                        .badge-registered{background:#e8f5e9;color:#2e7d32;padding:2px 8px;border-radius:10px;font-size:.8rem}
                        .badge-failed{background:#ffebee;color:#c62828;padding:2px 8px;border-radius:10px;font-size:.8rem}
                        .pagination{margin-top:12px;font-size:.85rem;color:#666}
                        .pagination a{color:#1565c0;margin:0 6px}
                        .detail-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px 24px;margin-bottom:20px}
                        .detail-label{font-size:.75rem;font-weight:bold;text-transform:uppercase;color:#666}
                        h2{margin-bottom:16px}
                        a.back-link{color:#1565c0;font-size:.85rem;display:inline-block;margin-top:12px}
                        .error-msg{color:#c62828;background:#ffebee;padding:10px;border-radius:4px;margin-bottom:12px}
                        .empty-msg{color:#666;padding:40px;text-align:center}
                        """.trimIndent(),
                    )
                }
            }
        }
        body {
            nav {
                a(href = "/files", classes = if (activeTab == "search") "active" else null) { +"File Search" }
                a(href = "/missing", classes = if (activeTab == "missing") "active" else null) { +"Missing Files" }
            }
            div(classes = "container") { block() }
        }
    }
```

- [ ] **Step 2: Compile to verify**

```bash
cd status-gui && mvn compile -q
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add status-gui/src/main/kotlin/mlid/enghub/statusgui/templates/Layout.kt
git commit -m "feat(status-gui): add shared Layout template"
```

---

## Task 5: File Search and Detail

**Files:**
- Create: `status-gui/src/test/kotlin/mlid/enghub/statusgui/FakeFileQueryRepository.kt`
- Create: `status-gui/src/test/kotlin/mlid/enghub/statusgui/routing/FileSearchRoutesTest.kt`
- Create: `status-gui/src/main/kotlin/mlid/enghub/statusgui/templates/FileSearchPage.kt`
- Create: `status-gui/src/main/kotlin/mlid/enghub/statusgui/templates/FileDetailPage.kt`
- Create: `status-gui/src/main/kotlin/mlid/enghub/statusgui/routing/FileSearchRoutes.kt`

- [ ] **Step 1: Create `FakeFileQueryRepository.kt`**

```kotlin
package mlid.enghub.statusgui

import mlid.enghub.statusgui.repository.DeliveryRow
import mlid.enghub.statusgui.repository.FileQueryRepository
import mlid.enghub.statusgui.repository.FileRow
import mlid.enghub.statusgui.repository.PagedResult
import java.time.LocalDateTime

class FakeFileQueryRepository : FileQueryRepository {
    var searchResult = PagedResult(emptyList(), 0)
    var findByIdResult: FileRow? = null
    var findDeliveriesResult: List<DeliveryRow> = emptyList()
    var findMissingResult = PagedResult(emptyList(), 0)

    override fun search(uploaderId: String?, bucket: String?, status: String?, since: LocalDateTime?, page: Int, size: Int) = searchResult
    override fun findById(id: String) = findByIdResult
    override fun findDeliveries(fileId: String) = findDeliveriesResult
    override fun findMissing(consumerId: String, bucket: String?, since: LocalDateTime, page: Int, size: Int) = findMissingResult
}

fun sampleFileRow(
    id: String = "test-id-001",
    filename: String = "report_Q1.csv",
    bucket: String = "finance",
    uploaderId: String = "uploader-01",
    status: String = "REGISTERED",
) = FileRow(
    id = id,
    bucket = bucket,
    reportId = "r1",
    reportCategory = "cat1",
    objectKey = "finance/report_Q1.csv",
    filename = filename,
    contentType = "text/csv",
    fileSize = 48320,
    checksum = null,
    uploaderId = uploaderId,
    tags = null,
    status = status,
    remark = null,
    errorCode = null,
    registeredAt = LocalDateTime.of(2026, 6, 3, 9, 12, 34),
)

fun sampleDeliveryRow(fileId: String = "test-id-001") = DeliveryRow(
    id = "delivery-001",
    fileId = fileId,
    consumerId = "consumer-reporting",
    note = null,
    processedAt = LocalDateTime.of(2026, 6, 3, 9, 15, 2),
)
```

- [ ] **Step 2: Write the failing route tests**

Create `status-gui/src/test/kotlin/mlid/enghub/statusgui/routing/FileSearchRoutesTest.kt`:

```kotlin
package mlid.enghub.statusgui.routing

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import mlid.enghub.statusgui.FakeFileQueryRepository
import mlid.enghub.statusgui.repository.PagedResult
import mlid.enghub.statusgui.sampleDeliveryRow
import mlid.enghub.statusgui.sampleFileRow
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class FileSearchRoutesTest {

    @Test
    fun `GET files renders search form and results`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.searchResult = PagedResult(listOf(sampleFileRow()), 1)
        application { fileSearchRoutes(repo) }
        val response = client.get("/files")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "report_Q1.csv")
        assertContains(body, "finance")
        assertContains(body, "uploader-01")
    }

    @Test
    fun `GET files shows empty message when no results`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.searchResult = PagedResult(emptyList(), 0)
        application { fileSearchRoutes(repo) }
        val response = client.get("/files")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "No files found")
    }

    @Test
    fun `GET files shows FAILED status badge`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.searchResult = PagedResult(listOf(sampleFileRow(status = "FAILED")), 1)
        application { fileSearchRoutes(repo) }
        val body = client.get("/files").bodyAsText()
        assertContains(body, "badge-failed")
    }

    @Test
    fun `GET files shows pagination when more than one page`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.searchResult = PagedResult(listOf(sampleFileRow()), 45)
        application { fileSearchRoutes(repo) }
        val body = client.get("/files").bodyAsText()
        assertContains(body, "Next")
        assertContains(body, "45")
    }

    @Test
    fun `GET files id returns detail page`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.findByIdResult = sampleFileRow()
        repo.findDeliveriesResult = listOf(sampleDeliveryRow())
        application { fileSearchRoutes(repo) }
        val body = client.get("/files/test-id-001").bodyAsText()
        assertContains(body, "test-id-001")
        assertContains(body, "consumer-reporting")
        assertContains(body, "finance/report_Q1.csv")
    }

    @Test
    fun `GET files id returns 404 for unknown file`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.findByIdResult = null
        application { fileSearchRoutes(repo) }
        val response = client.get("/files/no-such-id")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertContains(response.bodyAsText(), "File not found")
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
cd status-gui && mvn test -Dtest=FileSearchRoutesTest -q 2>&1 | tail -5
```

Expected: compilation error — `fileSearchRoutes` does not exist yet.

- [ ] **Step 4: Create `templates/FileSearchPage.kt`**

```kotlin
package mlid.enghub.statusgui.templates

import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import mlid.enghub.statusgui.repository.FileRow
import java.time.LocalDate

fun renderFileSearch(
    rows: List<FileRow>,
    total: Long,
    page: Int,
    uploaderId: String?,
    bucket: String?,
    status: String?,
    since: LocalDate?,
): String = layout("File Search", "search") {
    h2 { +"File Search" }
    form(action = "/files", method = FormMethod.get, classes = "filter-bar") {
        label {
            +"Uploader ID"
            textInput(name = "uploaderId") {
                placeholder = "any"
                value = uploaderId ?: ""
            }
        }
        label {
            +"Bucket"
            textInput(name = "bucket") {
                placeholder = "any"
                value = bucket ?: ""
            }
        }
        label {
            +"Status"
            select {
                name = "status"
                option { value = ""; +"any" }
                option {
                    value = "REGISTERED"
                    selected = status == "REGISTERED"
                    +"REGISTERED"
                }
                option {
                    value = "FAILED"
                    selected = status == "FAILED"
                    +"FAILED"
                }
            }
        }
        label {
            +"Since"
            textInput(name = "since") {
                type = "date"
                value = since?.toString() ?: ""
            }
        }
        button { +"Search" }
    }
    if (rows.isEmpty()) {
        p(classes = "empty-msg") { +"No files found." }
    } else {
        table {
            thead {
                tr {
                    th { +"ID" }
                    th { +"Filename" }
                    th { +"Bucket" }
                    th { +"Uploader" }
                    th { +"Status" }
                    th { +"Registered At" }
                }
            }
            tbody {
                rows.forEach { row ->
                    tr {
                        td { a(href = "/files/${row.id}") { +row.id.take(8) + "…" } }
                        td { +row.filename }
                        td { +row.bucket }
                        td { +row.uploaderId }
                        td {
                            val css = if (row.status == "FAILED") "badge-failed" else "badge-registered"
                            span(classes = css) { +row.status }
                        }
                        td { +row.registeredAt.toString().replace("T", " ").take(16) }
                    }
                }
            }
        }
        div(classes = "pagination") {
            +"Showing ${page * 20 + 1}–${minOf((page + 1) * 20, total.toInt())} of $total"
            if (page > 0) {
                a(href = searchUrl(uploaderId, bucket, status, since, page - 1)) { +" ← Prev" }
            }
            if ((page + 1) * 20 < total) {
                a(href = searchUrl(uploaderId, bucket, status, since, page + 1)) { +" Next →" }
            }
        }
    }
}

private fun searchUrl(uploaderId: String?, bucket: String?, status: String?, since: LocalDate?, page: Int): String {
    val params = buildList {
        if (!uploaderId.isNullOrBlank()) add("uploaderId=$uploaderId")
        if (!bucket.isNullOrBlank()) add("bucket=$bucket")
        if (!status.isNullOrBlank()) add("status=$status")
        if (since != null) add("since=$since")
        if (page > 0) add("page=$page")
    }
    return "/files" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
}
```

- [ ] **Step 5: Create `templates/FileDetailPage.kt`**

The `field` helper is a private file-level `DIV` extension so it has full access to the kotlinx.html DSL inside the `detail-grid` block.

```kotlin
package mlid.enghub.statusgui.templates

import kotlinx.html.DIV
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import mlid.enghub.statusgui.repository.DeliveryRow
import mlid.enghub.statusgui.repository.FileRow

fun renderFileDetail(file: FileRow, deliveries: List<DeliveryRow>): String =
    layout("File Detail", "search") {
        a(href = "/files", classes = "back-link") { +"← Back to search" }
        h2 { +"File: ${file.filename}" }
        div(classes = "detail-grid") {
            field("ID", file.id)
            div {
                div(classes = "detail-label") { +"Status" }
                div {
                    val css = if (file.status == "FAILED") "badge-failed" else "badge-registered"
                    span(classes = css) { +file.status }
                }
            }
            field("Filename", file.filename)
            field("Bucket", file.bucket)
            field("Report ID", file.reportId)
            field("Report Category", file.reportCategory)
            field("Uploader", file.uploaderId)
            field("File Size", "${file.fileSize} bytes")
            field("Content Type", file.contentType)
            field("Checksum", file.checksum)
            field("Object Key", file.objectKey)
            field("Tags", file.tags)
            field("Remark", file.remark)
            field("Error Code", file.errorCode)
            field("Registered At", file.registeredAt.toString().replace("T", " "))
        }
        h3 { +"Delivery Records" }
        if (deliveries.isEmpty()) {
            p(classes = "empty-msg") { +"No consumer has processed this file yet." }
        } else {
            table {
                thead {
                    tr {
                        th { +"Consumer" }
                        th { +"Processed At" }
                        th { +"Note" }
                    }
                }
                tbody {
                    deliveries.forEach { d ->
                        tr {
                            td { +d.consumerId }
                            td { +d.processedAt.toString().replace("T", " ").take(19) }
                            td { +(d.note ?: "—") }
                        }
                    }
                }
            }
        }
    }

private fun DIV.field(labelText: String, value: String?) {
    div {
        div(classes = "detail-label") { +labelText }
        div { +(value ?: "—") }
    }
}
```

- [ ] **Step 6: Create `routing/FileSearchRoutes.kt`**

```kotlin
package mlid.enghub.statusgui.routing

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import mlid.enghub.statusgui.repository.FileQueryRepository
import mlid.enghub.statusgui.templates.layout
import mlid.enghub.statusgui.templates.renderFileDetail
import mlid.enghub.statusgui.templates.renderFileSearch
import java.time.LocalDate

fun Application.fileSearchRoutes(repo: FileQueryRepository) {
    routing {
        get("/files") {
            val uploaderId = call.request.queryParameters["uploaderId"]?.takeIf { it.isNotBlank() }
            val bucket = call.request.queryParameters["bucket"]?.takeIf { it.isNotBlank() }
            val status = call.request.queryParameters["status"]?.takeIf { it.isNotBlank() }
            val since = call.request.queryParameters["since"]?.takeIf { it.isNotBlank() }
                ?.let { LocalDate.parse(it).atStartOfDay() }
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val result = repo.search(uploaderId, bucket, status, since, page)
            call.respondText(
                renderFileSearch(result.rows, result.total, page, uploaderId, bucket, status, since?.toLocalDate()),
                ContentType.Text.Html,
            )
        }
        get("/files/{id}") {
            val id = call.parameters["id"] ?: return@get call.respondText(
                "Bad request", ContentType.Text.Plain, HttpStatusCode.BadRequest,
            )
            val file = repo.findById(id) ?: return@get call.respondText(
                layout("Not Found", "search") { kotlinx.html.p { +"File not found." } },
                ContentType.Text.Html,
                HttpStatusCode.NotFound,
            )
            val deliveries = repo.findDeliveries(id)
            call.respondText(renderFileDetail(file, deliveries), ContentType.Text.Html)
        }
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

```bash
cd status-gui && mvn test -Dtest=FileSearchRoutesTest
```

Expected: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 8: Commit**

```bash
git add status-gui/src/
git commit -m "feat(status-gui): add file search and detail pages with route tests"
```

---

## Task 6: Missing Files Feature

**Files:**
- Create: `status-gui/src/test/kotlin/mlid/enghub/statusgui/routing/MissingFilesRoutesTest.kt`
- Create: `status-gui/src/main/kotlin/mlid/enghub/statusgui/templates/MissingFilesPage.kt`
- Create: `status-gui/src/main/kotlin/mlid/enghub/statusgui/routing/MissingFilesRoutes.kt`

- [ ] **Step 1: Write the failing route tests**

Create `status-gui/src/test/kotlin/mlid/enghub/statusgui/routing/MissingFilesRoutesTest.kt`:

```kotlin
package mlid.enghub.statusgui.routing

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import mlid.enghub.statusgui.FakeFileQueryRepository
import mlid.enghub.statusgui.repository.PagedResult
import mlid.enghub.statusgui.sampleFileRow
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class MissingFilesRoutesTest {

    @Test
    fun `GET missing without consumerId shows empty prompt`() = testApplication {
        val repo = FakeFileQueryRepository()
        application { missingFilesRoutes(repo) }
        val body = client.get("/missing").bodyAsText()
        assertContains(body, "Consumer ID")
        assertContains(body, "Missing Files")
    }

    @Test
    fun `GET missing with consumerId shows results`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.findMissingResult = PagedResult(listOf(sampleFileRow(filename = "missing_report.csv")), 1)
        application { missingFilesRoutes(repo) }
        val body = client.get("/missing?consumerId=consumer-a").bodyAsText()
        assertContains(body, "missing_report.csv")
    }

    @Test
    fun `GET missing with consumerId shows no results message`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.findMissingResult = PagedResult(emptyList(), 0)
        application { missingFilesRoutes(repo) }
        val body = client.get("/missing?consumerId=consumer-a").bodyAsText()
        assertEquals(HttpStatusCode.OK, client.get("/missing?consumerId=consumer-a").status)
        assertContains(body, "No missing files")
    }

    @Test
    fun `GET missing shows pagination when results exceed page size`() = testApplication {
        val repo = FakeFileQueryRepository()
        repo.findMissingResult = PagedResult(listOf(sampleFileRow()), 50)
        application { missingFilesRoutes(repo) }
        val body = client.get("/missing?consumerId=consumer-a").bodyAsText()
        assertContains(body, "Next")
        assertContains(body, "50")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd status-gui && mvn test -Dtest=MissingFilesRoutesTest -q 2>&1 | tail -5
```

Expected: compilation error — `missingFilesRoutes` does not exist yet.

- [ ] **Step 3: Create `templates/MissingFilesPage.kt`**

```kotlin
package mlid.enghub.statusgui.templates

import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import mlid.enghub.statusgui.repository.FileRow
import java.time.LocalDate

fun renderMissingFiles(
    rows: List<FileRow>,
    total: Long,
    page: Int,
    consumerId: String?,
    bucket: String?,
    since: LocalDate?,
    searched: Boolean,
): String = layout("Missing Files", "missing") {
    h2 { +"Missing Files" }
    form(action = "/missing", method = FormMethod.get, classes = "filter-bar") {
        label {
            +"Consumer ID *"
            textInput(name = "consumerId") {
                placeholder = "required"
                value = consumerId ?: ""
            }
        }
        label {
            +"Bucket"
            textInput(name = "bucket") {
                placeholder = "any"
                value = bucket ?: ""
            }
        }
        label {
            +"Since"
            textInput(name = "since") {
                type = "date"
                value = since?.toString() ?: ""
            }
        }
        button { +"Search" }
    }
    when {
        !searched -> p(classes = "empty-msg") { +"Enter a Consumer ID to find files not yet processed by that consumer." }
        rows.isEmpty() -> p(classes = "empty-msg") { +"No missing files for this consumer." }
        else -> {
            table {
                thead {
                    tr {
                        th { +"ID" }
                        th { +"Filename" }
                        th { +"Bucket" }
                        th { +"Uploader" }
                        th { +"Status" }
                        th { +"Registered At" }
                    }
                }
                tbody {
                    rows.forEach { row ->
                        tr {
                            td { a(href = "/files/${row.id}") { +row.id.take(8) + "…" } }
                            td { +row.filename }
                            td { +row.bucket }
                            td { +row.uploaderId }
                            td {
                                val css = if (row.status == "FAILED") "badge-failed" else "badge-registered"
                                span(classes = css) { +row.status }
                            }
                            td { +row.registeredAt.toString().replace("T", " ").take(16) }
                        }
                    }
                }
            }
            div(classes = "pagination") {
                +"Showing ${page * 20 + 1}–${minOf((page + 1) * 20, total.toInt())} of $total"
                if (page > 0) {
                    a(href = missingUrl(consumerId, bucket, since, page - 1)) { +" ← Prev" }
                }
                if ((page + 1) * 20 < total) {
                    a(href = missingUrl(consumerId, bucket, since, page + 1)) { +" Next →" }
                }
            }
        }
    }
}

private fun missingUrl(consumerId: String?, bucket: String?, since: LocalDate?, page: Int): String {
    val params = buildList {
        if (!consumerId.isNullOrBlank()) add("consumerId=$consumerId")
        if (!bucket.isNullOrBlank()) add("bucket=$bucket")
        if (since != null) add("since=$since")
        if (page > 0) add("page=$page")
    }
    return "/missing" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
}
```

- [ ] **Step 4: Create `routing/MissingFilesRoutes.kt`**

```kotlin
package mlid.enghub.statusgui.routing

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import mlid.enghub.statusgui.repository.FileQueryRepository
import mlid.enghub.statusgui.templates.renderMissingFiles
import java.time.LocalDate
import java.time.LocalDateTime

fun Application.missingFilesRoutes(repo: FileQueryRepository) {
    routing {
        get("/missing") {
            val consumerId = call.request.queryParameters["consumerId"]?.takeIf { it.isNotBlank() }
            val bucket = call.request.queryParameters["bucket"]?.takeIf { it.isNotBlank() }
            val since = call.request.queryParameters["since"]?.takeIf { it.isNotBlank() }
                ?.let { LocalDate.parse(it) }
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            if (consumerId == null) {
                call.respondText(
                    renderMissingFiles(emptyList(), 0, 0, null, null, null, searched = false),
                    ContentType.Text.Html,
                )
                return@get
            }
            val sinceDateTime = since?.atStartOfDay() ?: LocalDateTime.now().minusSeconds(86400)
            val result = repo.findMissing(consumerId, bucket, sinceDateTime, page)
            call.respondText(
                renderMissingFiles(result.rows, result.total, page, consumerId, bucket, since, searched = true),
                ContentType.Text.Html,
            )
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cd status-gui && mvn test -Dtest=MissingFilesRoutesTest
```

Expected: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 6: Commit**

```bash
git add status-gui/src/
git commit -m "feat(status-gui): add missing files page with route tests"
```

---

## Task 7: Wire Main.kt and Verify

**Files:**
- Modify: `status-gui/src/main/kotlin/mlid/enghub/statusgui/Main.kt`

- [ ] **Step 1: Complete `Main.kt`**

```kotlin
package mlid.enghub.statusgui

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import mlid.enghub.statusgui.repository.ExposedFileQueryRepository
import mlid.enghub.statusgui.routing.fileSearchRoutes
import mlid.enghub.statusgui.routing.missingFilesRoutes
import mlid.enghub.statusgui.templates.layout

fun main() {
    val db = connectDatabase()
    val repo = ExposedFileQueryRepository()
    val port = System.getenv("GUI_PORT")?.toInt() ?: 8090

    embeddedServer(Netty, port = port) {
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respondText(
                    layout("Error", "search") {
                        kotlinx.html.p(classes = "error-msg") { +"Internal error: ${cause.message}" }
                    },
                    ContentType.Text.Html,
                    HttpStatusCode.InternalServerError,
                )
            }
        }
        fileSearchRoutes(repo)
        missingFilesRoutes(repo)
    }.start(wait = true)
}
```

- [ ] **Step 2: Run all tests to confirm nothing broke**

```bash
cd status-gui && mvn test
```

Expected: all tests pass (`Tests run: 18, Failures: 0, Errors: 0`).

- [ ] **Step 3: Build the fat JAR**

```bash
cd status-gui && mvn package -DskipTests -q
```

Expected: `BUILD SUCCESS`. Produces `target/status-gui-1.0.0-SNAPSHOT.jar`.

- [ ] **Step 4: Update root `.gitignore` to exclude Testcontainers Docker cache**

Ensure `status-gui/target/` is covered (it is if the root `.gitignore` already has `target/`). Verify:

```bash
grep "target" /home/brandy/projects/file-exchange-hub/.gitignore
```

If missing, add:

```
status-gui/target/
```

- [ ] **Step 5: Final commit**

```bash
git add status-gui/src/main/kotlin/mlid/enghub/statusgui/Main.kt .gitignore
git commit -m "feat(status-gui): wire Main.kt — Ktor app complete and all tests passing"
```

---

## Running the App

### Prerequisites

Create the read-only DB user:

```sql
CREATE USER 'gui_reader'@'%' IDENTIFIED BY '<password>';
GRANT SELECT ON <database>.file_metadata TO 'gui_reader'@'%';
GRANT SELECT ON <database>.file_delivery TO 'gui_reader'@'%';
FLUSH PRIVILEGES;
```

### Start

```bash
cd status-gui
DB_HOST=localhost DB_PORT=3306 DB_NAME=<db> DB_USER=gui_reader DB_PASS=<password> \
  java -jar target/status-gui-1.0.0-SNAPSHOT.jar
```

Open http://localhost:8090/files
