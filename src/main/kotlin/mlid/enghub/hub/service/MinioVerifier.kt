package mlid.enghub.hub.service

import jakarta.enterprise.context.ApplicationScoped
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException

interface ObjectStoreVerifier {
    fun exists(
        bucket: String,
        objectKey: String,
    ): Boolean
}

@ApplicationScoped
class MinioVerifier(
    private val s3Client: S3Client,
) : ObjectStoreVerifier {
    override fun exists(
        bucket: String,
        objectKey: String,
    ): Boolean =
        try {
            s3Client.headObject(
                HeadObjectRequest
                    .builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build(),
            )
            true
        } catch (e: NoSuchKeyException) {
            false
        }
}
