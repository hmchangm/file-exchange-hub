package tw.brandy.ironman.hub.domain

import java.time.Instant
import java.util.UUID

enum class FileStatus { REGISTERED, FAILED }

data class FileMetadata(
    val id: String = UUID.randomUUID().toString(),
    val bucket: String,
    val reportId: String,
    val reportCategory: String,
    val objectKey: String,
    val filename: String,
    val contentType: String,
    val fileSize: Long,
    val checksum: String? = null,
    val uploaderId: String,
    val tags: String? = null,
    val status: FileStatus = FileStatus.REGISTERED,
    val remark: String? = null,
    val errorCode: String? = null,
    val registeredAt: Instant = Instant.now(),
)
