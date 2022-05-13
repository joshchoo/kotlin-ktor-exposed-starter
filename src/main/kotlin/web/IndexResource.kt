package web

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.index() {

    val indexPage = javaClass.getResource("/index.html").readText()

    get("/") {
        call.respondText(indexPage, ContentType.Text.Html)
    }
}
