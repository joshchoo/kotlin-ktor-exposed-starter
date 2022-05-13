import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.commandLineEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.routing.Routing
import io.ktor.server.websocket.WebSockets
import service.DatabaseFactory
import service.WidgetService
import util.JsonMapper
import web.index
import web.widget

fun Application.module() {
    install(DefaultHeaders)
    install(CallLogging)
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(JsonMapper.defaultMapper)
    }

    install(ContentNegotiation) {
        json(JsonMapper.defaultMapper)
    }

    DatabaseFactory.connectAndMigrate()

    val widgetService = WidgetService()

    install(Routing) {
        index()
        widget(widgetService)
    }
}

fun main(args: Array<String>) {
    embeddedServer(Netty, commandLineEnvironment(args)).start(wait = true)
}
