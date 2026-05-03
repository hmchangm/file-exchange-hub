package tw.brandy.ironman.hub.repository

import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.mutiny.mysqlclient.MySQLPool
import io.vertx.mutiny.sqlclient.Tuple
import jakarta.enterprise.context.ApplicationScoped
import tw.brandy.ironman.hub.domain.FileDelivery
import java.time.ZoneOffset

@ApplicationScoped
class FileDeliveryRepository(private val pool: MySQLPool) {

    suspend fun insertIgnore(delivery: FileDelivery) {
        pool.preparedQuery(
            "INSERT IGNORE INTO file_delivery (id, file_id, consumer_id, note, processed_at) VALUES (?, ?, ?, ?, ?)"
        ).execute(Tuple.of(
            delivery.id, delivery.fileId, delivery.consumerId, delivery.note,
            delivery.processedAt.atOffset(ZoneOffset.UTC).toLocalDateTime()
        )).map { Unit }
            .awaitSuspending()
    }
}
