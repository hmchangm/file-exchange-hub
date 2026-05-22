package mlid.enghub.hub.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import java.lang.reflect.Proxy

class MinioVerifierTest {
    @Test
    fun `returns true when object exists`() {
        val verifier = MinioVerifier(s3ClientReturning(HeadObjectResponse.builder().build()))
        assertTrue(verifier.exists("my-bucket", "path/to/file.pdf"))
    }

    @Test
    fun `returns false when object does not exist`() {
        val verifier = MinioVerifier(s3ClientThrowing(NoSuchKeyException.builder().build()))
        assertFalse(verifier.exists("my-bucket", "path/to/missing.pdf"))
    }

    private fun s3ClientReturning(response: HeadObjectResponse): S3Client = s3Client { response }

    private fun s3ClientThrowing(error: RuntimeException): S3Client = s3Client { throw error }

    private fun s3Client(headObject: () -> HeadObjectResponse): S3Client =
        Proxy.newProxyInstance(
            S3Client::class.java.classLoader,
            arrayOf(S3Client::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "headObject" -> headObject()
                "serviceName" -> "s3"
                "close" -> Unit
                else -> error("Unexpected S3Client call: ${method.name}")
            }
        } as S3Client
}
