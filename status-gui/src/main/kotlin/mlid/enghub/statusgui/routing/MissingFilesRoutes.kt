package mlid.enghub.statusgui.routing

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import mlid.enghub.statusgui.repository.FileQueryRepository
import mlid.enghub.statusgui.templates.renderMissingFiles
import java.time.LocalDate
import java.time.LocalDateTime

fun Application.missingFilesRoutes(repo: FileQueryRepository) {
    routing {
        get("/missing") {
            val consumerId = call.request.queryParameters["consumerId"]?.takeIf { it.isNotBlank() }
            val bucket = call.request.queryParameters["bucket"]?.takeIf { it.isNotBlank() }
            val since = call.request.queryParameters["since"]?.takeIf { it.isNotBlank() }
                ?.let { LocalDate.parse(it) }
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            if (consumerId == null) {
                call.respondText(
                    renderMissingFiles(emptyList(), 0, 0, null, null, null, searched = false),
                    ContentType.Text.Html,
                )
                return@get
            }
            val sinceDateTime = since?.atStartOfDay() ?: LocalDateTime.now().minusSeconds(86400)
            val result = repo.findMissing(consumerId, bucket, sinceDateTime, page)
            call.respondText(
                renderMissingFiles(result.rows, result.total, page, consumerId, bucket, since, searched = true),
                ContentType.Text.Html,
            )
        }
    }
}
