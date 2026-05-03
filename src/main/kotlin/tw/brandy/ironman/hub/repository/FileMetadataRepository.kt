package tw.brandy.ironman.hub.repository

import io.smallrye.mutiny.Uni
import io.vertx.mutiny.mariadbclient.MariaDBPool
import io.vertx.mutiny.sqlclient.Row
import io.vertx.mutiny.sqlclient.Tuple
import jakarta.enterprise.context.ApplicationScoped
import tw.brandy.ironman.hub.domain.FileMetadata
import tw.brandy.ironman.hub.domain.FileStatus
import java.time.Instant
import java.time.ZoneOffset

@ApplicationScoped
class FileMetadataRepository(private val pool: MariaDBPool) {

    fun findById(id: String): Uni<FileMetadata?> =
        pool.preparedQuery("SELECT * FROM file_metadata WHERE id = ?")
            .execute(Tuple.of(id))
            .map { rows -> rows.firstOrNull()?.toFileMetadata() }

    fun insert(metadata: FileMetadata): Uni<Unit> =
        pool.preparedQuery("""
            INSERT INTO file_metadata (id, bucket, report_id, report_category, object_key,
                filename, content_type, file_size, checksum, uploader_id, tags,
                status, remark, error_code, registered_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent())
            .execute(Tuple.of(
                metadata.id, metadata.bucket, metadata.reportId, metadata.reportCategory,
                metadata.objectKey, metadata.filename, metadata.contentType, metadata.fileSize,
                metadata.checksum, metadata.uploaderId, metadata.tags,
                metadata.status.name, metadata.remark, metadata.errorCode,
                metadata.registeredAt.atOffset(ZoneOffset.UTC).toLocalDateTime()
            ))
            .map { Unit }

    fun search(uploaderId: String?, bucket: String?, page: Int, size: Int): Uni<Pair<List<FileMetadata>, Long>> {
        val conditions = mutableListOf("status = ?")
        val params = mutableListOf<Any?>(FileStatus.REGISTERED.name)
        if (uploaderId != null) { conditions += "uploader_id = ?"; params += uploaderId }
        if (bucket != null) { conditions += "bucket = ?"; params += bucket }
        val where = conditions.joinToString(" AND ")
        val offset = page * size
        return pool.preparedQuery("SELECT COUNT(*) FROM file_metadata WHERE $where")
            .execute(Tuple.from(params))
            .flatMap { countRows ->
                val total = countRows.first().getLong(0) ?: 0L
                pool.preparedQuery(
                    "SELECT * FROM file_metadata WHERE $where ORDER BY registered_at DESC LIMIT ? OFFSET ?"
                ).execute(Tuple.from(params + listOf(size, offset)))
                    .map { rows -> rows.map { it.toFileMetadata() } to total }
            }
    }

    fun findMissing(consumerId: String, bucket: String?, since: Instant, page: Int, size: Int): Uni<Pair<List<FileMetadata>, Long>> {
        val bucketClause = if (bucket != null) "AND fm.bucket = ? " else ""
        val params = mutableListOf<Any?>(
            FileStatus.REGISTERED.name,
            since.atOffset(ZoneOffset.UTC).toLocalDateTime(),
            consumerId
        )
        if (bucket != null) params += bucket
        val baseSql = """
            FROM file_metadata fm
            WHERE fm.status = ?
            AND fm.registered_at >= ?
            AND NOT EXISTS (
                SELECT 1 FROM file_delivery fd
                WHERE fd.file_id = fm.id AND fd.consumer_id = ?
            )
            $bucketClause
        """.trimIndent()
        val offset = page * size
        return pool.preparedQuery("SELECT COUNT(*) $baseSql")
            .execute(Tuple.from(params))
            .flatMap { countRows ->
                val total = countRows.first().getLong(0) ?: 0L
                pool.preparedQuery("SELECT fm.* $baseSql ORDER BY fm.registered_at LIMIT ? OFFSET ?")
                    .execute(Tuple.from(params + listOf(size, offset)))
                    .map { rows -> rows.map { it.toFileMetadata() } to total }
            }
    }

    private fun Row.toFileMetadata(): FileMetadata = FileMetadata(
        id = getString("id"),
        bucket = getString("bucket"),
        reportId = getString("report_id"),
        reportCategory = getString("report_category"),
        objectKey = getString("object_key"),
        filename = getString("filename"),
        contentType = getString("content_type"),
        fileSize = getLong("file_size"),
        checksum = getString("checksum"),
        uploaderId = getString("uploader_id"),
        tags = getString("tags"),
        status = FileStatus.valueOf(getString("status")),
        remark = getString("remark"),
        errorCode = getString("error_code"),
        registeredAt = getLocalDateTime("registered_at").toInstant(ZoneOffset.UTC)
    )
}
