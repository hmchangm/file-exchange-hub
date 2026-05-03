package tw.brandy.ironman.hub.service

import io.smallrye.mutiny.Uni
import io.smallrye.mutiny.infrastructure.Infrastructure
import jakarta.enterprise.context.ApplicationScoped
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
    fun register(request: RegisterFileRequest): Uni<RegisterFileResponse> =
        Uni.createFrom().item { verifier.exists(request.bucket, request.objectKey) }
            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
            .flatMap { exists ->
                if (!exists) {
                    Uni.createFrom().failure(
                        ObjectNotFoundException("Object not found in MinIO: ${request.bucket}/${request.objectKey}")
                    )
                } else {
                    val metadata = FileMetadata(
                        bucket = request.bucket,
                        reportId = request.reportId,
                        reportCategory = request.reportCategory,
                        objectKey = request.objectKey,
                        filename = request.filename,
                        contentType = request.contentType,
                        fileSize = request.fileSize,
                        checksum = request.checksum,
                        uploaderId = request.uploaderId,
                        tags = request.tags?.let { Json.encodeToString(it) },
                        status = FileStatus.REGISTERED,
                        registeredAt = Instant.now()
                    )
                    repository.insert(metadata).map {
                        val event = FileRegisteredEvent.from(metadata, request.tags)
                        val published = publisher.publish(event)
                        RegisterFileResponse(
                            id = metadata.id,
                            status = metadata.status.name,
                            eventPublished = published,
                            registeredAt = metadata.registeredAt.toString()
                        )
                    }
                }
            }
}
