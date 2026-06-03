package mlid.enghub.statusgui

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import kotlinx.html.p
import mlid.enghub.statusgui.repository.ExposedFileQueryRepository
import mlid.enghub.statusgui.routing.fileSearchRoutes
import mlid.enghub.statusgui.routing.missingFilesRoutes
import mlid.enghub.statusgui.templates.layout

fun main() {
    connectDatabase() // registers HikariCP pool as Exposed's implicit default database
    val repo = ExposedFileQueryRepository()
    val port = System.getenv("GUI_PORT")?.toInt() ?: 8090

    embeddedServer(Netty, port = port) {
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respondText(
                    layout("Error", "search") {
                        p(classes = "error-msg") { +"An unexpected error occurred. Please try again." }
                    },
                    ContentType.Text.Html,
                    HttpStatusCode.InternalServerError,
                )
            }
        }
        fileSearchRoutes(repo)
        missingFilesRoutes(repo)
    }.start(wait = true)
}
