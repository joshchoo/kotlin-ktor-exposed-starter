package web

import common.ServerTest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.restassured.RestAssured.delete
import io.restassured.RestAssured.get
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import model.ChangeType
import model.NewWidget
import model.Widget
import model.WidgetNotification
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import util.JsonMapper.defaultMapper

class WidgetResourceTest : ServerTest() {

    @Test
    fun testCreateWidget() {
        // when
        val newWidget = NewWidget(null, "widget1", 12)
        val created = addWidget(newWidget)

        val retrieved = get("/widgets/{id}", created.id)
            .then()
            .extract().to<Widget>()

        // then
        assertThat(created.name).isEqualTo(newWidget.name)
        assertThat(created.quantity).isEqualTo(newWidget.quantity)

        assertThat(created).isEqualTo(retrieved)
    }

    @Test
    fun testGetWidgets() {
        // when
        val widget1 = NewWidget(null, "widget1", 10)
        val widget2 = NewWidget(null, "widget2", 5)
        addWidget(widget1)
        addWidget(widget2)

        val widgets = get("/widgets")
            .then()
            .statusCode(200)
            .extract().to<List<Widget>>()

        assertThat(widgets).hasSize(2)
        assertThat(widgets).extracting("name").containsExactlyInAnyOrder(widget1.name, widget2.name)
        assertThat(widgets).extracting("quantity").containsExactlyInAnyOrder(widget1.quantity, widget2.quantity)
    }

    @Test
    fun testUpdateWidget() {
        // when
        val widget1 = NewWidget(null, "widget1", 10)
        val saved = addWidget(widget1)

        // then
        val update = NewWidget(saved.id, "updated", 46)
        val updated = given()
            .contentType(ContentType.JSON)
            .bodyJson(update)
            .When()
            .put("/widgets")
            .then()
            .statusCode(200)
            .extract().to<Widget>()

        assertThat(updated).isNotNull
        assertThat(updated.id).isEqualTo(update.id)
        assertThat(updated.name).isEqualTo(update.name)
        assertThat(updated.quantity).isEqualTo(update.quantity)
    }

    @Test
    fun testDeleteWidget() {
        // when
        val newWidget = NewWidget(null, "widget1", 12)
        val created = addWidget(newWidget)

        // then
        delete("/widgets/{id}", created.id)
            .then()
            .statusCode(200)

        get("/widgets/{id}", created.id)
            .then()
            .statusCode(404)
    }

    @Nested
    inner class ErrorCases {

        @Test
        fun testUpdateInvalidWidget() {
            val updatedWidget = NewWidget(-1, "invalid", -1)
            given()
                .contentType(ContentType.JSON)
                .bodyJson(updatedWidget)
                .When()
                .put("/widgets")
                .then()
                .statusCode(404)
        }

        @Test
        fun testDeleteInvalidWidget() {
            delete("/widgets/{id}", "-1")
                .then()
                .statusCode(404)
        }

        @Test
        fun testGetInvalidWidget() {
            get("/widgets/{id}", "-1")
                .then()
                .statusCode(404)
        }
    }

    @Nested
    inner class WebSocketNotifications {
        @Test
        fun testGetNotificationForWidgetAdd() {
            // when
            val newWidget = NewWidget(null, "widgetForSocket", 23)

            val client = HttpClient {
                install(WebSockets)
            }

            runBlocking {
                client.webSocket(host = "localhost", port = 8081, path = "/updates") {
                    val created = addWidget(newWidget)

                    val frame = incoming.receive()
                    assertThat(frame).isInstanceOf(Frame.Text::class.java)
                    val textFrame = frame as Frame.Text
                    val value = withContext(Dispatchers.IO) {
                        defaultMapper.decodeFromString<WidgetNotification>(textFrame.readText())
                    }
                    assertThat(value.type).isEqualTo(ChangeType.CREATE)
                    assertThat(value.entity).isNotNull.also {
                        it.extracting(Widget::name.name).isEqualTo(newWidget.name)
                        it.extracting(Widget::id.name).isEqualTo(created.id)
                    }

                    close(CloseReason(CloseReason.Codes.NORMAL, "Finished test"))
                }
            }
        }

        @Test
        fun testSendListenerMessage() {
            val client = HttpClient {
                install(WebSockets)
            }

            runBlocking {
                client.webSocket(host = "localhost", port = 8081, path = "/updates") {
                    outgoing.send(Frame.Text("sample message"))
                    close(CloseReason(CloseReason.Codes.NORMAL, "Finished test"))
                }
            }
        }
    }

    private fun addWidget(widget: NewWidget): Widget {
        return given()
            .contentType(ContentType.JSON)
            .bodyJson(widget)
            .When()
            .post("/widgets")
            .then()
            .statusCode(201)
            .extract().to()
    }
}
