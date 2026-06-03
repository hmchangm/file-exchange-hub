package mlid.enghub.statusgui.routing

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.html.p
import mlid.enghub.statusgui.repository.FileQueryRepository
import mlid.enghub.statusgui.templates.layout
import mlid.enghub.statusgui.templates.renderFileDetail
import mlid.enghub.statusgui.templates.renderFileSearch
import java.time.LocalDate

fun Application.fileSearchRoutes(repo: FileQueryRepository) {
    routing {
        get("/files") {
            val uploaderId = call.request.queryParameters["uploaderId"]?.takeIf { it.isNotBlank() }
            val bucket = call.request.queryParameters["bucket"]?.takeIf { it.isNotBlank() }
            val status = call.request.queryParameters["status"]?.takeIf { it.isNotBlank() }
            val since = call.request.queryParameters["since"]?.takeIf { it.isNotBlank() }
                ?.let { LocalDate.parse(it).atStartOfDay() }
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val result = repo.search(uploaderId, bucket, status, since, page)
            call.respondText(
                renderFileSearch(result.rows, result.total, page, uploaderId, bucket, status, since?.toLocalDate()),
                ContentType.Text.Html,
            )
        }
        get("/files/{id}") {
            val id = call.parameters["id"] ?: return@get call.respondText(
                "Bad request", ContentType.Text.Plain, HttpStatusCode.BadRequest,
            )
            val file = repo.findById(id) ?: return@get call.respondText(
                layout("Not Found", "search") { p { +"File not found." } },
                ContentType.Text.Html,
                HttpStatusCode.NotFound,
            )
            val deliveries = repo.findDeliveries(id)
            call.respondText(renderFileDetail(file, deliveries), ContentType.Text.Html)
        }
    }
}
