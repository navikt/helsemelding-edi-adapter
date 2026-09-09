package no.nav.helsemelding.ediadapter.client

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import no.nav.helsemelding.ediadapter.model.v3.Notification
import no.nav.helsemelding.ediadapter.model.v3.NotificationType
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

class NotificationStreamSpec : StringSpec(
    {
        "streamNotifications parses multiline data and ignores connected events and comments" {
            testApplication {
                application {
                    routing {
                        get("/api/v3/notifications/stream") {
                            call.request.queryParameters.getAll("herIds") shouldBe listOf("123", "456")
                            call.request.queryParameters["offset"] shouldBe "2147483646"
                            call.respondText(
                                """
                                : heartbeat

                                event: connected
                                data:

                                event: notification
                                data: {"type":"NewMessage",
                                data: "notificationReceiverHerId":123,"offset":2147483647}

                                """.trimIndent() + "\n",
                                ContentType.Text.EventStream
                            )
                        }
                    }
                }
                HttpEdiAdapterClient({ streamingClient() }, "http://localhost").use { client ->
                    withTimeout(5000.milliseconds) {
                        client.streamNotifications(listOf(123, 456), 2147483646).first()
                    }
                        .shouldBeRight(notification(2147483647))
                }
            }
        }

        "streamNotifications decodes all notification types in the same stream" {
            testApplication {
                application {
                    routing {
                        get("/api/v3/notifications/stream") {
                            call.respondText(
                                """
                                event: notification
                                data: {"type":"NewMessage",
                                data: "notificationReceiverHerId":123,"offset":1}

                                event: notification
                                data: {"type":"RefusedMessage",
                                data: "notificationReceiverHerId":123,"offset":2}

                                event: notification
                                data: {"type":"MessageSentStateUpdated",
                                data: "notificationReceiverHerId":123,"offset":3}

                                event: notification
                                data: {"type":"MessageApprecInfoUpdated",
                                data: "notificationReceiverHerId":123,"offset":4}

                                event: notification
                                data: {"type":"MessageDeliveryStateUpdated",
                                data: "notificationReceiverHerId":123,"offset":5}

                                """.trimIndent() + "\n",
                                ContentType.Text.EventStream
                            )
                        }
                    }
                }
                HttpEdiAdapterClient({ streamingClient() }, "http://localhost").use { client ->
                    val notifications = withTimeout(5000.milliseconds) {
                        client.streamNotifications(123, offset = 0).take(5).toList()
                    }
                        .map { it.shouldBeRight() }

                    notifications.map { it.type } shouldBe listOf(
                        NotificationType.NEW_MESSAGE,
                        NotificationType.REFUSED_MESSAGE,
                        NotificationType.MESSAGE_SENT_STATE_UPDATED,
                        NotificationType.MESSAGE_APPREC_INFO_UPDATED,
                        NotificationType.MESSAGE_DELIVERY_STATE_UPDATED
                    )
                    notifications.map { it.offset } shouldBe listOf(1, 2, 3, 4, 5)
                }
            }
        }

        "streamNotifications reconnects after EOF using the latest emitted offset" {
            testApplication {
                val offsets = mutableListOf<String?>()
                application {
                    routing {
                        get("/api/v3/notifications/stream") {
                            offsets += call.request.queryParameters["offset"]
                            call.respondText(event(offsets.size), ContentType.Text.EventStream)
                        }
                    }
                }
                HttpEdiAdapterClient({ streamingClient() }, "http://localhost").use { client ->
                    val notifications = withTimeout(5000.milliseconds) {
                        client.streamNotifications(listOf(123), 0).take(2).toList()
                    }
                    notifications.map { it.shouldBeRight().offset } shouldBe listOf(1, 2)
                    offsets shouldBe listOf("0", "1")
                }
            }
        }

        "streamNotifications resumes from the first notification when no initial offset is supplied" {
            testApplication {
                val offsets = mutableListOf<String?>()
                application {
                    routing {
                        get("/api/v3/notifications/stream") {
                            offsets += call.request.queryParameters["offset"]
                            call.respondText(event(1), ContentType.Text.EventStream)
                        }
                    }
                }
                HttpEdiAdapterClient({ streamingClient() }, "http://localhost").use { client ->
                    withTimeout(5000.milliseconds) {
                        client.streamNotifications(listOf(123)).take(2).toList()
                    }
                        .size shouldBe 2
                    offsets shouldBe listOf(null, "1")
                }
            }
        }

        "streamNotifications retries a temporary upstream error" {
            testApplication {
                var attempts = 0
                application {
                    routing {
                        get("/api/v3/notifications/stream") {
                            attempts++
                            if (attempts == 1) {
                                call.respond(HttpStatusCode.ServiceUnavailable)
                            } else {
                                call.respondText(event(1), ContentType.Text.EventStream)
                            }
                        }
                    }
                }
                HttpEdiAdapterClient({ streamingClient() }, "http://localhost").use { client ->
                    withTimeout(5000.milliseconds) {
                        client.streamNotifications(listOf(123), 0).first()
                    }
                        .shouldBeRight(notification(1))
                    attempts shouldBe 2
                }
            }
        }

        "streamNotifications retries a transport failure and uses the supplied client authentication configuration" {
            testApplication {
                var attempts = 0
                val failFirstRequest = createClientPlugin("FailFirstRequest") {
                    onRequest { _, _ ->
                        attempts++
                        if (attempts == 1) throw IOException("Connection refused")
                    }
                }
                application {
                    routing {
                        get("/api/v3/notifications/stream") {
                            call.request.headers["Authorization"] shouldBe "Bearer test-token"
                            call.respondText(event(1), ContentType.Text.EventStream)
                        }
                    }
                }
                HttpEdiAdapterClient({
                    createClient {
                        expectSuccess = false
                        install(SSE) { maxReconnectionAttempts = 0 }
                        install(failFirstRequest)
                        defaultRequest { headers.append("Authorization", "Bearer test-token") }
                    }
                }, "http://localhost").use { client ->
                    withTimeout(5000.milliseconds) {
                        client.streamNotifications(listOf(123), 0).first()
                    }
                        .shouldBeRight(notification(1))
                    attempts shouldBe 2
                }
            }
        }

        "streamNotifications propagates failures wrapped by Ktor without reconnecting" {
            testApplication {
                var attempts = 0
                val failingPlugin = createClientPlugin("FailingPlugin") {
                    onRequest { _, _ ->
                        attempts++
                        error("Invalid client configuration")
                    }
                }
                HttpEdiAdapterClient({
                    createClient {
                        install(SSE) { maxReconnectionAttempts = 0 }
                        install(failingPlugin)
                    }
                }, "http://localhost").use { client ->
                    shouldThrow<IllegalStateException> {
                        withTimeout(5000.milliseconds) { client.streamNotifications(123).collect() }
                    }
                        .message shouldBe "Invalid client configuration"
                    attempts shouldBe 1
                }
            }
        }

        "streamNotifications rejects a successful response with the wrong content type" {
            testApplication {
                application {
                    routing {
                        get("/api/v3/notifications/stream") {
                            call.respondText("Unexpected response", ContentType.Text.Plain)
                        }
                    }
                }
                HttpEdiAdapterClient({ streamingClient() }, "http://localhost").use { client ->
                    val results =
                        withTimeout(5000.milliseconds) { client.streamNotifications(listOf(123), 0).toList() }
                    results.single().shouldBeLeft().shouldBeInstanceOf<EdiAdapterError.Decoding>()
                }
            }
        }

        "streamNotifications ends with a typed error when access is denied" {
            testApplication {
                var attempts = 0
                application {
                    routing {
                        get("/api/v3/notifications/stream") {
                            attempts++
                            call.respondText(
                                """
                                {
                                  "status": 403,
                                  "title": "Forbidden"
                                }
                                """.trimIndent(),
                                ContentType.parse("application/problem+json"),
                                HttpStatusCode.Forbidden
                            )
                        }
                    }
                }
                HttpEdiAdapterClient({ streamingClient() }, "http://localhost").use { client ->
                    val results =
                        withTimeout(5000.milliseconds) { client.streamNotifications(listOf(123), 0).toList() }
                    val error = results.single().shouldBeLeft().shouldBeInstanceOf<EdiAdapterError.Api>()
                    error.status shouldBe 403
                    error.problem?.title shouldBe "Forbidden"
                    attempts shouldBe 1
                }
            }
        }

        "streamNotifications ends with a decoding error for invalid notification data" {
            testApplication {
                var attempts = 0
                application {
                    routing {
                        get("/api/v3/notifications/stream") {
                            attempts++
                            call.respondText("event: notification\ndata: invalid\n\n", ContentType.Text.EventStream)
                        }
                    }
                }
                HttpEdiAdapterClient({ streamingClient() }, "http://localhost").use { client ->
                    val results =
                        withTimeout(5000.milliseconds) { client.streamNotifications(listOf(123), 0).toList() }
                    results.single().shouldBeLeft().shouldBeInstanceOf<EdiAdapterError.Decoding>()
                    attempts shouldBe 1
                }
            }
        }

        "streamNotifications propagates collector failures without reconnecting" {
            testApplication {
                var attempts = 0
                application {
                    routing {
                        get("/api/v3/notifications/stream") {
                            attempts++
                            call.respondText(event(1), ContentType.Text.EventStream)
                        }
                    }
                }
                HttpEdiAdapterClient({ streamingClient() }, "http://localhost").use { client ->
                    val failure = IllegalStateException("Processing failed")
                    shouldThrow<IllegalStateException> {
                        withTimeout(5000.milliseconds) {
                            client.streamNotifications(listOf(123), 0).collect { throw failure }
                        }
                    } shouldBe failure
                    attempts shouldBe 1
                }
            }
        }

        "streamNotifications keeps the reconnect offset local to each collection" {
            testApplication {
                val offsets = mutableListOf<String?>()
                application {
                    routing {
                        get("/api/v3/notifications/stream") {
                            val offset = call.request.queryParameters["offset"]
                            offsets += offset
                            call.respondText(event(offset!!.toInt() + 1), ContentType.Text.EventStream)
                        }
                    }
                }
                HttpEdiAdapterClient({ streamingClient() }, "http://localhost").use { client ->
                    val stream = client.streamNotifications(listOf(123), 0)
                    withTimeout(5000.milliseconds) {
                        stream.take(2).toList().map { it.shouldBeRight().offset } shouldBe listOf(1, 2)
                        stream.take(2).toList().map { it.shouldBeRight().offset } shouldBe listOf(1, 2)
                    }
                    offsets shouldBe listOf("0", "1", "0", "1")
                }
            }
        }

        "streamNotifications closes the connection when collection stops" {
            testApplication {
                val closed = CompletableDeferred<Unit>()
                application {
                    routing {
                        get("/api/v3/notifications/stream") {
                            call.respondTextWriter(ContentType.Text.EventStream) {
                                try {
                                    write(event(1))
                                    flush()
                                    while (true) {
                                        delay(10.milliseconds)
                                        write(": heartbeat\n\n")
                                        flush()
                                    }
                                } finally {
                                    closed.complete(Unit)
                                }
                            }
                        }
                    }
                }
                HttpEdiAdapterClient({ streamingClient() }, "http://localhost").use { client ->
                    withTimeout(5000.milliseconds) {
                        client.streamNotifications(listOf(123), 0).first().shouldBeRight(notification(1))
                        closed.await()
                    }
                }
            }
        }

        "streamNotifications treats HTTP 204 as the end of the stream" {
            testApplication {
                application {
                    routing {
                        get("/api/v3/notifications/stream") {
                            call.request.queryParameters["offset"] shouldBe null
                            call.respond(HttpStatusCode.NoContent)
                        }
                    }
                }
                HttpEdiAdapterClient({ streamingClient() }, "http://localhost").use { client ->
                    withTimeout(5000.milliseconds) {
                        client.streamNotifications(listOf(123)).toList()
                    } shouldBe emptyList()
                }
            }
        }
    }
)

private fun notification(offset: Int) = Notification(
    type = NotificationType.NEW_MESSAGE,
    notificationReceiverHerId = 123,
    offset = offset
)

private fun event(offset: Int) = "event: notification\ndata: ${Json.encodeToString(notification(offset))}\n\n"

private fun ApplicationTestBuilder.streamingClient() = createClient {
    expectSuccess = false
    install(SSE) { maxReconnectionAttempts = 0 }
}
