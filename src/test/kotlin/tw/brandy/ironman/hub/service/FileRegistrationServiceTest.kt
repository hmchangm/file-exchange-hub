package tw.brandy.ironman.hub.service

import io.smallrye.mutiny.Uni
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.brandy.ironman.hub.domain.FileMetadata
import tw.brandy.ironman.hub.domain.FileRegisteredEvent
import tw.brandy.ironman.hub.repository.FileMetadataStore
import tw.brandy.ironman.hub.resource.dto.RegisterFileRequest

class FileRegistrationServiceTest {

    private val verifier = FakeObjectStoreVerifier()
    private val repository = FakeFileMetadataStore()
    private val publisher = FakeFileRegistrationEventPublisher()
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
        verifier.objectExists = true
        publisher.publishResult = true
        val result = service.register(validRequest).await().indefinitely()
        assertTrue(result.eventPublished)
        assertEquals("REGISTERED", result.status)
        assertEquals(1, repository.insertCount)
    }

    @Test
    fun `returns eventPublished false when NATS publish fails`() {
        verifier.objectExists = true
        publisher.publishResult = false
        val result = service.register(validRequest).await().indefinitely()
        assertFalse(result.eventPublished)
        assertEquals("REGISTERED", result.status)
    }

    @Test
    fun `throws ObjectNotFoundException when file not found in MinIO`() {
        verifier.objectExists = false
        assertThrows(ObjectNotFoundException::class.java) {
            service.register(validRequest).await().indefinitely()
        }
        assertEquals(0, repository.insertCount)
    }

    @Test
    fun `registers successfully when checksum is null (bypass)`() {
        val req = validRequest.copy(checksum = null)
        verifier.objectExists = true
        publisher.publishResult = true
        val result = service.register(req).await().indefinitely()
        assertEquals("REGISTERED", result.status)
    }

    private class FakeObjectStoreVerifier : ObjectStoreVerifier {
        var objectExists = true
        override fun exists(bucket: String, objectKey: String): Boolean = objectExists
    }

    private class FakeFileMetadataStore : FileMetadataStore {
        var insertCount = 0
        var inserted: FileMetadata? = null

        override fun insert(metadata: FileMetadata): Uni<Unit> {
            insertCount += 1
            inserted = metadata
            return Uni.createFrom().item(Unit)
        }
    }

    private class FakeFileRegistrationEventPublisher : FileRegistrationEventPublisher {
        var publishResult = true
        var published: FileRegisteredEvent? = null

        override fun publish(event: FileRegisteredEvent): Boolean {
            published = event
            return publishResult
        }
    }
}
