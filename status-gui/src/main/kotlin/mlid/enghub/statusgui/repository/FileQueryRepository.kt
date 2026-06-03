package mlid.enghub.statusgui.repository

import java.time.LocalDateTime

data class FileRow(
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
    val tags: String?,
    val status: String,
    val remark: String?,
    val errorCode: String?,
    val registeredAt: LocalDateTime,
)

data class DeliveryRow(
    val id: String,
    val fileId: String,
    val consumerId: String,
    val note: String?,
    val processedAt: LocalDateTime,
)

data class PagedResult(val rows: List<FileRow>, val total: Long)

interface FileQueryRepository {
    fun search(
        uploaderId: String?,
        bucket: String?,
        status: String?,
        since: LocalDateTime?,
        page: Int,
        size: Int = 20,
    ): PagedResult

    fun findById(id: String): FileRow?

    fun findDeliveries(fileId: String): List<DeliveryRow>

    fun findMissing(
        consumerId: String,
        bucket: String?,
        since: LocalDateTime,
        page: Int,
        size: Int = 20,
    ): PagedResult
}
