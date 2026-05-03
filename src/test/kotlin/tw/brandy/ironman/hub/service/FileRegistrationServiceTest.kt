package tw.brandy.ironman.hub.service

import io.mockk.*
import io.smallrye.mutiny.Uni
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tw.brandy.ironman.hub.repository.FileMetadataRepository
import tw.brandy.ironman.hub.resource.dto.RegisterFileRequest

class FileRegistrationServiceTest {

    private val verifier = mockk<MinioVerifier>()
    private val repository = mockk<FileMetadataRepository>()
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
        every { repository.insert(any()) } returns Uni.createFrom().item(Unit)
        every { publisher.publish(any()) } returns true
        val result = service.register(validRequest).await().indefinitely()
        assertTrue(result.eventPublished)
        assertEquals("REGISTERED", result.status)
        verify { repository.insert(any()) }
    }

    @Test
    fun `returns eventPublished false when NATS publish fails`() {
        every { verifier.exists("incoming", "reports/report.pdf") } returns true
        every { repository.insert(any()) } returns Uni.createFrom().item(Unit)
        every { publisher.publish(any()) } returns false
        val result = service.register(validRequest).await().indefinitely()
        assertFalse(result.eventPublished)
        assertEquals("REGISTERED", result.status)
    }

    @Test
    fun `throws ObjectNotFoundException when file not found in MinIO`() {
        every { verifier.exists("incoming", "reports/report.pdf") } returns false
        assertThrows(ObjectNotFoundException::class.java) {
            service.register(validRequest).await().indefinitely()
        }
        verify(exactly = 0) { repository.insert(any()) }
    }

    @Test
    fun `registers successfully when checksum is null (bypass)`() {
        val req = validRequest.copy(checksum = null)
        every { verifier.exists("incoming", "reports/report.pdf") } returns true
        every { repository.insert(any()) } returns Uni.createFrom().item(Unit)
        every { publisher.publish(any()) } returns true
        val result = service.register(req).await().indefinitely()
        assertEquals("REGISTERED", result.status)
    }
}
