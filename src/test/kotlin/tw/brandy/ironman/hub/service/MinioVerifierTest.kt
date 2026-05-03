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
