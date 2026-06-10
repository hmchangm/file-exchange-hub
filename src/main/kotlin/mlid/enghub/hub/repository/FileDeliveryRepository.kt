package mlid.enghub.hub.repository

import io.smallrye.mutiny.coroutines.awaitSuspending
import io.vertx.mutiny.sqlclient.Pool
import io.vertx.mutiny.sqlclient.Tuple
import jakarta.enterprise.context.ApplicationScoped
import mlid.enghub.hub.domain.FileDelivery
import java.time.ZoneOffset

@ApplicationScoped
class FileDeliveryRepository(
    private val pool: Pool,
) {
    suspend fun insertIgnore(delivery: FileDelivery) {
        pool
            .preparedQuery(
                """
                INSERT INTO file_delivery (id, file_id, consumer_id, note, processed_at)
                SELECT ?, ?, ?, ?, ? FROM DUAL
                WHERE NOT EXISTS (
                    SELECT 1 FROM file_delivery WHERE file_id = ? AND consumer_id = ?
                )
                """.trimIndent(),
            ).execute(
                Tuple.from(
                    listOf(
                        delivery.id,
                        delivery.fileId,
                        delivery.consumerId,
                        delivery.note,
                        delivery.processedAt.atOffset(ZoneOffset.UTC).toLocalDateTime(),
                        delivery.fileId,
                        delivery.consumerId,
                    ),
                ),
            ).map { Unit }
            .awaitSuspending()
    }
}
