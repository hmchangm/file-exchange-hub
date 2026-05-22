package tw.brandy.ironman.hub.resource.dto

import kotlinx.serialization.Serializable
import tw.brandy.ironman.hub.domain.FileMetadata

@Serializable
data class FileMetadataDto(
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
    val status: String,
    val remark: String?,
    val errorCode: String?,
    val registeredAt: String,
) {
    companion object {
        fun from(
            m: FileMetadata,
            tags: Map<String, String>?,
        ): FileMetadataDto =
            FileMetadataDto(
                id = m.id,
                bucket = m.bucket,
                reportId = m.reportId,
                reportCategory = m.reportCategory,
                objectKey = m.objectKey,
                filename = m.filename,
                contentType = m.contentType,
                fileSize = m.fileSize,
                checksum = m.checksum,
                uploaderId = m.uploaderId,
                tags = tags,
                status = m.status.name,
                remark = m.remark,
                errorCode = m.errorCode,
                registeredAt = m.registeredAt.toString(),
            )
    }
}
