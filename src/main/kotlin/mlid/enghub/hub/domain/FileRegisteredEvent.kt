package mlid.enghub.hub.domain

import kotlinx.serialization.Serializable

@Serializable
data class FileRegisteredEvent(
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
    val tags: Map<String, String>?,
    val registeredAt: String,
) {
    companion object {
        fun from(
            metadata: FileMetadata,
            tags: Map<String, String>?,
        ): FileRegisteredEvent =
            FileRegisteredEvent(
                id = metadata.id,
                bucket = metadata.bucket,
                reportId = metadata.reportId,
                reportCategory = metadata.reportCategory,
                objectKey = metadata.objectKey,
                filename = metadata.filename,
                contentType = metadata.contentType,
                fileSize = metadata.fileSize,
                checksum = metadata.checksum,
                uploaderId = metadata.uploaderId,
                tags = tags,
                registeredAt = metadata.registeredAt.toString(),
            )
    }
}
