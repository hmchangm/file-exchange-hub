package mlid.enghub.statusgui

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val port = System.getenv("GUI_PORT")?.toInt() ?: 8090
    embeddedServer(Netty, port = port) {
    }.start(wait = true)
}
