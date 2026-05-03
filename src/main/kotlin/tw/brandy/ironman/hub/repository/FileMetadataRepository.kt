package tw.brandy.ironman.hub.repository

import io.quarkus.hibernate.orm.panache.kotlin.PanacheRepositoryBase
import io.quarkus.panache.common.Page
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import tw.brandy.ironman.hub.domain.FileMetadata
import tw.brandy.ironman.hub.domain.FileStatus
import java.time.Instant

@ApplicationScoped
class FileMetadataRepository : PanacheRepositoryBase<FileMetadata, String> {

    fun findByIdOrNull(id: String): FileMetadata? = findById(id)

    fun search(uploaderId: String?, bucket: String?, page: Int, size: Int): Pair<List<FileMetadata>, Long> {
        val conditions = mutableListOf("status = :status")
        val params = mutableMapOf<String, Any>("status" to FileStatus.REGISTERED)
        if (uploaderId != null) { conditions += "uploaderId = :uploaderId"; params["uploaderId"] = uploaderId }
        if (bucket != null) { conditions += "bucket = :bucket"; params["bucket"] = bucket }
        val q = find(conditions.joinToString(" and "), Sort.by("registeredAt").descending(), params)
        val total = q.count()
        val results = q.page(Page.of(page, size)).list()
        return results to total
    }

    fun findMissing(consumerId: String, bucket: String?, since: Instant, page: Int, size: Int): Pair<List<FileMetadata>, Long> {
        val bucketClause = if (bucket != null) "and fm.bucket = :bucket " else ""
        val params = mutableMapOf<String, Any>(
            "consumerId" to consumerId,
            "since" to since,
            "status" to FileStatus.REGISTERED
        )
        if (bucket != null) params["bucket"] = bucket
        val jpql = """
            from FileMetadata fm
            where fm.status = :status
            and fm.registeredAt >= :since
            $bucketClause
            and not exists (
                select 1 from FileDelivery fd
                where fd.fileId = fm.id and fd.consumerId = :consumerId
            )
        """.trimIndent()
        val q = find(jpql, Sort.by("registeredAt"), params)
        val total = q.count()
        val results = q.page(Page.of(page, size)).list()
        return results to total
    }
}
