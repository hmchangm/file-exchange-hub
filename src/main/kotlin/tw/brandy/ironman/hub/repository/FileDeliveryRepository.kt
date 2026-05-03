package tw.brandy.ironman.hub.repository

import io.smallrye.mutiny.Uni
import io.vertx.mutiny.mariadbclient.MariaDBPool
import io.vertx.mutiny.sqlclient.Tuple
import jakarta.enterprise.context.ApplicationScoped
import tw.brandy.ironman.hub.domain.FileDelivery
import java.time.ZoneOffset

@ApplicationScoped
class FileDeliveryRepository(private val pool: MariaDBPool) {

    fun insertIgnore(delivery: FileDelivery): Uni<Unit> =
        pool.preparedQuery(
            "INSERT IGNORE INTO file_delivery (id, file_id, consumer_id, note, processed_at) VALUES (?, ?, ?, ?, ?)"
        ).execute(Tuple.of(
            delivery.id, delivery.fileId, delivery.consumerId, delivery.note,
            delivery.processedAt.atOffset(ZoneOffset.UTC).toLocalDateTime()
        )).map { Unit }
}
