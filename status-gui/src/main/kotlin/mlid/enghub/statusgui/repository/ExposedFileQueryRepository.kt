package mlid.enghub.statusgui.repository

import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

internal object FileMetadataTable : Table("file_metadata") {
    val id = varchar("id", 36)
    val bucket = varchar("bucket", 255)
    val reportId = varchar("report_id", 255)
    val reportCategory = varchar("report_category", 255)
    val objectKey = varchar("object_key", 2000)
    val filename = varchar("filename", 255)
    val contentType = varchar("content_type", 128)
    val fileSize = long("file_size")
    val checksum = varchar("checksum", 256).nullable()
    val uploaderId = varchar("uploader_id", 255)
    val tags = text("tags").nullable()
    val status = varchar("status", 20)
    val remark = varchar("remark", 1024).nullable()
    val errorCode = varchar("error_code", 64).nullable()
    val registeredAt = datetime("registered_at")
    override val primaryKey = PrimaryKey(id)
}

internal object FileDeliveryTable : Table("file_delivery") {
    val id = varchar("id", 36)
    val fileId = varchar("file_id", 36)
    val consumerId = varchar("consumer_id", 255)
    val note = text("note").nullable()
    val processedAt = datetime("processed_at")
    override val primaryKey = PrimaryKey(id)
}

class ExposedFileQueryRepository : FileQueryRepository {

    override fun search(
        uploaderId: String?,
        bucket: String?,
        status: String?,
        since: LocalDateTime?,
        page: Int,
        size: Int,
    ): PagedResult = transaction {
        val query = FileMetadataTable.selectAll()
        if (uploaderId != null) query.andWhere { FileMetadataTable.uploaderId eq uploaderId }
        if (bucket != null) query.andWhere { FileMetadataTable.bucket eq bucket }
        if (status != null) query.andWhere { FileMetadataTable.status eq status }
        if (since != null) query.andWhere { FileMetadataTable.registeredAt greaterEq since }
        val total = query.count()
        val rows = query
            .orderBy(FileMetadataTable.registeredAt to SortOrder.DESC)
            .limit(size).offset((page.toLong() * size))
            .map { it.toFileRow() }
        PagedResult(rows, total)
    }

    override fun findById(id: String): FileRow? = transaction {
        FileMetadataTable.selectAll()
            .where { FileMetadataTable.id eq id }
            .singleOrNull()
            ?.toFileRow()
    }

    override fun findDeliveries(fileId: String): List<DeliveryRow> = transaction {
        FileDeliveryTable.selectAll()
            .where { FileDeliveryTable.fileId eq fileId }
            .map { it.toDeliveryRow() }
    }

    override fun findMissing(
        consumerId: String,
        bucket: String?,
        since: LocalDateTime,
        page: Int,
        size: Int,
    ): PagedResult = transaction {
        val query = FileMetadataTable
            .join(
                FileDeliveryTable,
                JoinType.LEFT,
                onColumn = FileMetadataTable.id,
                otherColumn = FileDeliveryTable.fileId,
                additionalConstraint = { FileDeliveryTable.consumerId eq consumerId },
            )
            .selectAll()
            .where { FileDeliveryTable.id.isNull() }
            .andWhere { FileMetadataTable.registeredAt greaterEq since }
        if (bucket != null) query.andWhere { FileMetadataTable.bucket eq bucket }
        val total = query.count()
        val rows = query
            .orderBy(FileMetadataTable.registeredAt to SortOrder.DESC)
            .limit(size).offset((page.toLong() * size))
            .map { it.toFileRow() }
        PagedResult(rows, total)
    }

    private fun ResultRow.toFileRow() = FileRow(
        id = this[FileMetadataTable.id],
        bucket = this[FileMetadataTable.bucket],
        reportId = this[FileMetadataTable.reportId],
        reportCategory = this[FileMetadataTable.reportCategory],
        objectKey = this[FileMetadataTable.objectKey],
        filename = this[FileMetadataTable.filename],
        contentType = this[FileMetadataTable.contentType],
        fileSize = this[FileMetadataTable.fileSize],
        checksum = this[FileMetadataTable.checksum],
        uploaderId = this[FileMetadataTable.uploaderId],
        tags = this[FileMetadataTable.tags],
        status = this[FileMetadataTable.status],
        remark = this[FileMetadataTable.remark],
        errorCode = this[FileMetadataTable.errorCode],
        registeredAt = this[FileMetadataTable.registeredAt],
    )

    private fun ResultRow.toDeliveryRow() = DeliveryRow(
        id = this[FileDeliveryTable.id],
        fileId = this[FileDeliveryTable.fileId],
        consumerId = this[FileDeliveryTable.consumerId],
        note = this[FileDeliveryTable.note],
        processedAt = this[FileDeliveryTable.processedAt],
    )
}
