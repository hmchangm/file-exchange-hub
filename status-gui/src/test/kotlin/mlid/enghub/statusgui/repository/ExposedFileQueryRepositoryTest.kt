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
