package mlid.enghub.hub.service

import io.smallrye.mutiny.coroutines.awaitSuspending
import io.smallrye.reactive.messaging.MutinyEmitter
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eclipse.microprofile.reactive.messaging.Channel
import mlid.enghub.hub.domain.FileRegisteredEvent

interface FileRegistrationEventPublisher {
    suspend fun publish(event: FileRegisteredEvent): Boolean
}

@ApplicationScoped
class FileEventPublisher(
    @Channel("files-registered") private val emitter: MutinyEmitter<String>,
) : FileRegistrationEventPublisher {
    override suspend fun publish(event: FileRegisteredEvent): Boolean =
        try {
            emitter.send(Json.encodeToString(event)).awaitSuspending()
            true
        } catch (e: Exception) {
            false
        }
}
