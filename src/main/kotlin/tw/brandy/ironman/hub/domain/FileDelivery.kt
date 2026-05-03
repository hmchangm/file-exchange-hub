package tw.brandy.ironman.hub.domain

import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "file_delivery",
    uniqueConstraints = [UniqueConstraint(columnNames = ["file_id", "consumer_id"])]
)
class FileDelivery : PanacheEntityBase {
    @Id
    var id: String = UUID.randomUUID().toString()

    @Column(name = "file_id", nullable = false)
    var fileId: String = ""

    @Column(name = "consumer_id", nullable = false)
    var consumerId: String = ""

    @Column(columnDefinition = "TEXT")
    var note: String? = null

    @Column(name = "processed_at", nullable = false)
    var processedAt: Instant = Instant.now()
}
