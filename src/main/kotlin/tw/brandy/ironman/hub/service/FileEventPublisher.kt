package tw.brandy.ironman.hub.service

import io.smallrye.reactive.messaging.MutinyEmitter
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eclipse.microprofile.reactive.messaging.Channel
import tw.brandy.ironman.hub.domain.FileRegisteredEvent

@ApplicationScoped
class FileEventPublisher(
    @Channel("files-registered") private val emitter: MutinyEmitter<String>
) {
    fun publish(event: FileRegisteredEvent): Boolean = try {
        emitter.sendAndAwait(Json.encodeToString(event))
        true
    } catch (e: Exception) {
        false
    }
}
