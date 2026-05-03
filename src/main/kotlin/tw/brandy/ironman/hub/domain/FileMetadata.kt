package tw.brandy.ironman.hub.domain

import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class FileStatus { REGISTERED, FAILED }

@Entity
@Table(name = "file_metadata")
class FileMetadata : PanacheEntityBase {
    @Id
    var id: String = UUID.randomUUID().toString()

    @Column(nullable = false)
    var bucket: String = ""

    @Column(name = "report_id", nullable = false)
    var reportId: String = ""

    @Column(name = "report_category", nullable = false)
    var reportCategory: String = ""

    @Column(name = "object_key", nullable = false, length = 2000)
    var objectKey: String = ""

    @Column(nullable = false)
    var filename: String = ""

    @Column(name = "content_type", nullable = false, length = 128)
    var contentType: String = ""

    @Column(name = "file_size", nullable = false)
    var fileSize: Long = 0

    @Column(length = 256)
    var checksum: String? = null

    @Column(name = "uploader_id", nullable = false)
    var uploaderId: String = ""

    @Column(columnDefinition = "JSON")
    var tags: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: FileStatus = FileStatus.REGISTERED

    @Column(length = 1024)
    var remark: String? = null

    @Column(name = "error_code", length = 64)
    var errorCode: String? = null

    @Column(name = "registered_at", nullable = false)
    var registeredAt: Instant = Instant.now()
}
