package tw.brandy.ironman.hub.domain

import java.time.Instant
import java.util.UUID

data class FileDelivery(
    val id: String = UUID.randomUUID().toString(),
    val fileId: String,
    val consumerId: String,
    val note: String? = null,
    val processedAt: Instant = Instant.now(),
)
