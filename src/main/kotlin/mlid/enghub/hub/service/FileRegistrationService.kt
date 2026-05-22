package mlid.enghub.hub.service

import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mlid.enghub.hub.domain.FileMetadata
import mlid.enghub.hub.domain.FileRegisteredEvent
import mlid.enghub.hub.domain.FileStatus
import mlid.enghub.hub.repository.FileMetadataStore
import mlid.enghub.hub.resource.dto.RegisterFileRequest
import mlid.enghub.hub.resource.dto.RegisterFileResponse
import java.time.Instant

class ObjectNotFoundException(
    message: String,
) : RuntimeException(message)

@ApplicationScoped
class FileRegistrationService(
    private val verifier: ObjectStoreVerifier,
    private val repository: FileMetadataStore,
    private val publisher: FileRegistrationEventPublisher,
) {
    suspend fun register(request: RegisterFileRequest): RegisterFileResponse {
        val exists =
            withContext(Dispatchers.IO) {
                verifier.exists(request.bucket, request.objectKey)
            }
        if (!exists) {
            throw ObjectNotFoundException("Object not found in MinIO: ${request.bucket}/${request.objectKey}")
        }

        val metadata =
            FileMetadata(
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
                registeredAt = Instant.now(),
            )
        repository.insert(metadata)

        val event = FileRegisteredEvent.from(metadata, request.tags)
        val published =
            withTimeoutOrNull(EVENT_PUBLISH_TIMEOUT_MS) {
                publisher.publish(event)
            } ?: false
        return RegisterFileResponse(
            id = metadata.id,
            status = metadata.status.name,
            eventPublished = published,
            registeredAt = metadata.registeredAt.toString(),
        )
    }

    private companion object {
        const val EVENT_PUBLISH_TIMEOUT_MS = 2_000L
    }
}
