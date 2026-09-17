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
import kotlin.uuid.Uuid

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
                                data: {"notificationId":"17aaeaa7-fa1e-4b60-a8e9-bdc31718dfc9","type":"NewMessage",
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
                                data: {"notificationId":"17aaeaa7-fa1e-4b60-a8e9-bdc31718dfc9","relatedMessageId":"733be787-0ad0-475a-98b7-00512caa9ccb","type":"NewMessage","notificationReceiverHerId":123,"offset":1}

                                event: notification
                                data: {"notificationId":"17aaeaa7-fa1e-4b60-a8e9-bdc31718dfc9","relatedMessageId":"733be787-0ad0-475a-98b7-00512caa9ccb","type":"RefusedMessage","notificationReceiverHerId":123,"offset":2}

                                event: notification
                                data: {"notificationId":"17aaeaa7-fa1e-4b60-a8e9-bdc31718dfc9","relatedMessageId":"733be787-0ad0-475a-98b7-00512caa9ccb","type":"MessageSentStateUpdated","notificationReceiverHerId":123,"offset":3}

                                event: notification
                                data: {"notificationId":"17aaeaa7-fa1e-4b60-a8e9-bdc31718dfc9","relatedMessageId":"70104e29-d573-4578-9ac7-1383410fffc3","type":"MessageApprecInfoUpdated","notificationReceiverHerId":123,"offset":4}

                                event: notification
                                data: {"notificationId":"17aaeaa7-fa1e-4b60-a8e9-bdc31718dfc9","relatedMessageId":"733be787-0ad0-475a-98b7-00512caa9ccb","type":"MessageDeliveryStateUpdated","notificationReceiverHerId":123,"offset":5}

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
                    notifications.map { it.offset } shouldBe listOf(1L, 2L, 3L, 4L, 5L)
                    notifications.map { it.relatedMessageId } shouldBe listOf(
                        Uuid.parse("733be787-0ad0-475a-98b7-00512caa9ccb"),
                        Uuid.parse("733be787-0ad0-475a-98b7-00512caa9ccb"),
                        Uuid.parse("733be787-0ad0-475a-98b7-00512caa9ccb"),
                        Uuid.parse("70104e29-d573-4578-9ac7-1383410fffc3"),
                        Uuid.parse("733be787-0ad0-475a-98b7-00512caa9ccb")
                    )
                }
            }
        }

        "streamNotifications accepts missing and null relatedMessageId" {
            testApplication {
                application {
                    routing {
                        get("/api/v3/notifications/stream") {
                            call.respondText(
                                """
                                event: notification
                                data: {"notificationId":"17aaeaa7-fa1e-4b60-a8e9-bdc31718dfc9","type":"NewMessage","notificationReceiverHerId":123,"offset":1}

                                event: notification
                                data: {"notificationId":"70104e29-d573-4578-9ac7-1383410fffc3","relatedMessageId":null,"type":"NewMessage","notificationReceiverHerId":123,"offset":2}

                                """.trimIndent() + "\n",
                                ContentType.Text.EventStream
                            )
                        }
                    }
                }
                HttpEdiAdapterClient({ streamingClient() }, "http://localhost").use { client ->
                    val notifications = withTimeout(5000.milliseconds) {
                        client.streamNotifications(123, offset = 0).take(2).toList()
                    }.map { it.shouldBeRight() }

                    notifications.map { it.offset } shouldBe listOf(1L, 2L)
                    notifications.map { it.relatedMessageId } shouldBe listOf(null, null)
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
                            call.respondText(event(offsets.size.toLong()), ContentType.Text.EventStream)
                        }
                    }
                }
                HttpEdiAdapterClient({ streamingClient() }, "http://localhost").use { client ->
                    val notifications = withTimeout(5000.milliseconds) {
                        client.streamNotifications(listOf(123), 0).take(2).toList()
                    }
                    notifications.map { it.shouldBeRight().offset } shouldBe listOf(1L, 2L)
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

        "streamNotifications retries after a temporary upstream error" {
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

        "streamNotifications retries after a transport failure and uses the supplied client authentication configuration" {
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
                            call.respondText(
                                """
                                event: notification
                                data: invalid

                                """.trimIndent() + "\n",
                                ContentType.Text.EventStream
                            )
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
                            call.respondText(event(offset!!.toLong() + 1), ContentType.Text.EventStream)
                        }
                    }
                }
                HttpEdiAdapterClient({ streamingClient() }, "http://localhost").use { client ->
                    val stream = client.streamNotifications(listOf(123), 0)
                    withTimeout(5000.milliseconds) {
                        stream.take(2).toList().map { it.shouldBeRight().offset } shouldBe listOf(1L, 2L)
                        stream.take(2).toList().map { it.shouldBeRight().offset } shouldBe listOf(1L, 2L)
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

private fun notification(offset: Long) = Notification(
    notificationId = Uuid.parse("17aaeaa7-fa1e-4b60-a8e9-bdc31718dfc9"),
    type = NotificationType.NEW_MESSAGE,
    notificationReceiverHerId = 123,
    offset = offset
)

private fun event(offset: Long) = "event: notification\ndata: ${Json.encodeToString(notification(offset))}\n\n"

private fun ApplicationTestBuilder.streamingClient() = createClient {
    expectSuccess = false
    install(SSE) { maxReconnectionAttempts = 0 }
}
