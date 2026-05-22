package tw.brandy.ironman.hub

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import org.testcontainers.containers.MinIOContainer
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
import java.nio.file.Files

class MinioTestResource : QuarkusTestResourceLifecycleManager {
    companion object {
        private val container = MinIOContainer("minio/minio:latest")
        lateinit var s3Client: S3Client
    }

    override fun start(): Map<String, String> {
        container.start()

        s3Client =
            S3Client
                .builder()
                .endpointOverride(URI.create(container.s3URL))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(container.userName, container.password),
                    ),
                ).region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()

        s3Client.createBucket(CreateBucketRequest.builder().bucket("incoming").build())

        val tempFile = Files.createTempFile("sample", ".pdf")
        tempFile.toFile().writeText("sample pdf content")
        s3Client.putObject(
            PutObjectRequest
                .builder()
                .bucket("incoming")
                .key("test/sample.pdf")
                .build(),
            tempFile,
        )
        Files.delete(tempFile)

        return mapOf(
            "quarkus.s3.endpoint-override" to container.s3URL,
            "quarkus.s3.aws.credentials.static-provider.access-key-id" to container.userName,
            "quarkus.s3.aws.credentials.static-provider.secret-access-key" to container.password,
            "quarkus.s3.path-style-access" to "true",
        )
    }

    override fun stop() {
        container.stop()
    }
}
