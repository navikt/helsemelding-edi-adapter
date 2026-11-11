package no.nav.helsemelding.ediadapter.client

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helsemelding.ediadapter.model.common.GetBusinessDocumentResponse
import no.nav.helsemelding.ediadapter.model.v3.AppRecStatus
import no.nav.helsemelding.ediadapter.model.v3.ApprecInfo
import no.nav.helsemelding.ediadapter.model.v3.DeleteNotificationsRequest
import no.nav.helsemelding.ediadapter.model.v3.DeliveryState
import no.nav.helsemelding.ediadapter.model.v3.GetMessageResponse
import no.nav.helsemelding.ediadapter.model.v3.GetNotificationsResponse
import no.nav.helsemelding.ediadapter.model.v3.GetStatusResponse
import no.nav.helsemelding.ediadapter.model.v3.GetUnreadNotificationsResponse
import no.nav.helsemelding.ediadapter.model.v3.MarkAsDownloadedRequest
import no.nav.helsemelding.ediadapter.model.v3.MshApiProblemDetails
import no.nav.helsemelding.ediadapter.model.v3.MshConfiguration
import no.nav.helsemelding.ediadapter.model.v3.Notification
import no.nav.helsemelding.ediadapter.model.v3.NotificationType
import no.nav.helsemelding.ediadapter.model.v3.PingResponse
import no.nav.helsemelding.ediadapter.model.v3.PostAppRecRequest
import no.nav.helsemelding.ediadapter.model.v3.PostApprecResponse
import no.nav.helsemelding.ediadapter.model.v3.PostMessageRequest
import no.nav.helsemelding.ediadapter.model.v3.PostMessageResponse
import no.nav.helsemelding.ediadapter.model.v3.ReceiveNotificationChannel
import no.nav.helsemelding.ediadapter.model.v3.SetMshConfigurationsRequest
import no.nav.helsemelding.ediadapter.model.v3.StatusInfo
import no.nav.helsemelding.ediadapter.model.v3.UnreadNotification
import java.io.IOException
import javax.net.ssl.SSLException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

class EdiAdapterClientSpec : StringSpec(
    {
        val id = Uuid.random()

        "getNotifications sends her ids, offset and notifications to fetch" {
            val expected = GetNotificationsResponse(
                listOf(
                    Notification(
                        notificationId = id,
                        type = NotificationType.NEW_MESSAGE,
                        notificationReceiverHerId = 123,
                        offset = 2147483647
                    )
                )
            )
            withClient({ request ->
                request.method shouldBe HttpMethod.Get
                request.url.fullPath shouldBe "/api/v3/notifications?herIds=123&herIds=456&offset=2147483646&notificationsToFetch=1000"
                respondJson(expected)
            }) { client ->
                client.getNotifications(listOf(123, 456), 2147483646, 1000).shouldBeRight(expected)
            }
        }

        "getNotifications without notifications to fetch omits the query parameter" {
            withClient({ request ->
                request.url.fullPath shouldBe "/api/v3/notifications?herIds=123&offset=0"
                respondJson(GetNotificationsResponse(emptyList()))
            }) { client ->
                client.getNotifications(listOf(123), 0).shouldBeRight(GetNotificationsResponse(emptyList()))
            }
        }

        "getUnreadNotifications sends her ids and notifications to fetch" {
            val expected = GetUnreadNotificationsResponse(
                listOf(
                    UnreadNotification(
                        notificationId = id,
                        type = NotificationType.NEW_MESSAGE,
                        notificationReceiverHerId = 123
                    ),
                    UnreadNotification(
                        notificationId = Uuid.random(),
                        type = NotificationType.REFUSED_MESSAGE,
                        notificationReceiverHerId = 456
                    )
                )
            )
            withClient({ request ->
                request.method shouldBe HttpMethod.Get
                request.url.fullPath shouldBe "/api/v3/notifications/unread?herIds=123&herIds=456&notificationsToFetch=2"
                respondJson(expected)
            }) { client ->
                client.getUnreadNotifications(listOf(123, 456), 2).shouldBeRight(expected)
            }
        }

        "getUnreadNotifications without notifications to fetch omits the query parameter" {
            withClient({ request ->
                request.url.fullPath shouldBe "/api/v3/notifications/unread?herIds=123"
                respondJson(GetUnreadNotificationsResponse(emptyList()))
            }) { client ->
                client.getUnreadNotifications(listOf(123)).shouldBeRight(GetUnreadNotificationsResponse(emptyList()))
            }
        }

        "getUnreadNotifications sends a single her id and notifications to fetch" {
            withClient({ request ->
                request.url.fullPath shouldBe "/api/v3/notifications/unread?herIds=123&notificationsToFetch=10"
                respondJson(GetUnreadNotificationsResponse(emptyList()))
            }) { client ->
                client.getUnreadNotifications(123, 10).shouldBeRight(GetUnreadNotificationsResponse(emptyList()))
            }
        }

        "getUnreadNotifications with a single her id and without notifications to fetch omits the query parameter" {
            withClient({ request ->
                request.url.fullPath shouldBe "/api/v3/notifications/unread?herIds=123"
                respondJson(GetUnreadNotificationsResponse(emptyList()))
            }) { client ->
                client.getUnreadNotifications(123).shouldBeRight(GetUnreadNotificationsResponse(emptyList()))
            }
        }

        "deleteNotifications sends notification ids using POST and accepts 204" {
            val body = DeleteNotificationsRequest(listOf(id, Uuid.random()))
            withClient({ request ->
                request.method shouldBe HttpMethod.Post
                request.url.fullPath shouldBe "/api/v3/notifications/delete"
                request.body.shouldBeInstanceOf<TextContent>().contentType.toString() shouldBe "application/json"
                request.body<DeleteNotificationsRequest>() shouldBe body
                respond("", HttpStatusCode.NoContent)
            }) { client -> client.deleteNotifications(body).shouldBeRight(Unit) }
        }

        "deleteNotifications returns 403 with problem details" {
            val problem = MshApiProblemDetails(status = 403, title = "Forbidden")
            withClient({ respondJson(problem, HttpStatusCode.Forbidden) }) { client ->
                client.deleteNotifications(DeleteNotificationsRequest(listOf(id)))
                    .shouldBeLeft(EdiAdapterError.Api(403, problem))
            }
        }

        "postMessage sends the V3 payload and decodes an accepted response" {
            val body = PostMessageRequest("PHhtbC8+", 123, listOf(456), "application/xml", "base64", "DIALOG_HELSEFAGLIG")
            withClient({ request ->
                request.method shouldBe HttpMethod.Post
                request.url.fullPath shouldBe "/api/v3/messages"
                request.body.shouldBeInstanceOf<TextContent>().contentType.toString() shouldBe "application/json"
                request.body<PostMessageRequest>() shouldBe body
                respondJson(PostMessageResponse(id), HttpStatusCode.Accepted)
            }) { client ->
                client.postMessage(body).shouldBeRight(PostMessageResponse(id))
            }
        }

        "getMessage returns V3 metadata" {
            val expected = GetMessageResponse(id, senderHerId = 123, receiverHerIds = listOf(456))
            withClient({ request ->
                request.method shouldBe HttpMethod.Get
                request.url.fullPath shouldBe "/api/v3/messages/$id"
                respondJson(expected)
            }) { client -> client.getMessage(id).shouldBeRight(expected) }
        }

        "getBusinessDocument returns the encoded document" {
            val expected = GetBusinessDocumentResponse("PHhtbC8+", "application/xml", "base64")
            withClient({ request ->
                request.method shouldBe HttpMethod.Get
                request.url.fullPath shouldBe "/api/v3/messages/$id/document"
                respondJson(expected)
            }) { client -> client.getBusinessDocument(id).shouldBeRight(expected) }
        }

        "getMessageStatus returns V3 status information" {
            val appRecId = Uuid.parse("68e60a2b-5990-408c-b99b-089d8657d6ed")
            val expected = GetStatusResponse(
                statusList = listOf(
                    StatusInfo(
                        receiverHerId = 456,
                        transportDeliveryState = DeliveryState.ACKNOWLEDGED,
                        sent = true,
                        apprecInfo = ApprecInfo(
                            appRecStatus = AppRecStatus.OK,
                            appRecId = appRecId
                        )
                    )
                )
            )
            withClient({ request ->
                request.method shouldBe HttpMethod.Get
                request.url.fullPath shouldBe "/api/v3/messages/$id/status"
                respond(
                    """
                    {
                      "statusList": [
                        {
                          "receiverHerId": 456,
                          "transportDeliveryState": "Acknowledged",
                          "sent": true,
                          "apprecInfo": {
                            "appRecStatus": "Ok",
                            "appRecId": "$appRecId"
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }) { client -> client.getMessageStatus(id).shouldBeRight(expected) }
        }

        "postApprec sends the sender in the V3 payload" {
            val body = PostAppRecRequest(123, AppRecStatus.OK)
            withClient({ request ->
                request.method shouldBe HttpMethod.Post
                request.url.fullPath shouldBe "/api/v3/messages/$id/apprec"
                request.body<PostAppRecRequest>() shouldBe body
                respondJson(PostApprecResponse(id), HttpStatusCode.Accepted)
            }) { client -> client.postApprec(id, body).shouldBeRight(PostApprecResponse(id)) }
        }

        "markMessageAsDownloaded sends the receiver and accepts an empty response" {
            val body = MarkAsDownloadedRequest(123)
            withClient({ request ->
                request.method shouldBe HttpMethod.Put
                request.url.fullPath shouldBe "/api/v3/messages/$id/downloaded"
                request.body<MarkAsDownloadedRequest>() shouldBe body
                respond("", HttpStatusCode.NoContent)
            }) { client -> client.markMessageAsDownloaded(id, body).shouldBeRight(Unit) }
        }

        "setMshConfigurations sends V3 configurations using PUT" {
            val body = SetMshConfigurationsRequest(listOf(MshConfiguration(123, ReceiveNotificationChannel.API)))
            withClient({ request ->
                request.method shouldBe HttpMethod.Put
                request.url.fullPath shouldBe "/api/v3/mshconfigurations"
                request.body<SetMshConfigurationsRequest>() shouldBe body
                respond("", HttpStatusCode.NoContent)
            }) { client -> client.setMshConfigurations(body).shouldBeRight(Unit) }
        }

        "deleteMshConfigurations sends repeated her ids" {
            withClient({ request ->
                request.method shouldBe HttpMethod.Delete
                request.url.fullPath shouldBe "/api/v3/mshconfigurations?herIds=123&herIds=456"
                respond("", HttpStatusCode.NoContent)
            }) { client -> client.deleteMshConfigurations(listOf(123, 456)).shouldBeRight(Unit) }
        }

        "ping decodes the response and ignores additional fields" {
            withClient({ request ->
                request.method shouldBe HttpMethod.Get
                request.url.fullPath shouldBe "/api/v3/ping"
                respond(
                    """
                    {
                      "response": "pong",
                      "futureField": true
                    }
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }) { client -> client.ping().shouldBeRight(PingResponse("pong")) }
        }

        "HTTP failures retain status and problem details" {
            val problem = MshApiProblemDetails(status = 400, title = "Bad Request", validationErrors = listOf("Invalid her id"))
            withClient({ respondJson(problem, HttpStatusCode.BadRequest) }) { client ->
                client.ping().shouldBeLeft(EdiAdapterError.Api(400, problem))
            }
        }

        "non-JSON HTTP failures retain the status" {
            withClient({ respond("Bad gateway", HttpStatusCode.BadGateway) }) { client ->
                client.ping().shouldBeLeft(EdiAdapterError.Api(502))
            }
        }

        "invalid successful responses return a decoding error" {
            var requests = 0
            withClient({ requests++; respond("not JSON") }) { client ->
                client.ping().shouldBeLeft().shouldBeInstanceOf<EdiAdapterError.Decoding>()
                requests shouldBe 1
            }
        }

        "GET requests retry transient HTTP statuses and succeed" {
            for (status in listOf(408, 429, 500, 502, 503, 504)) {
                var requests = 0
                withClient({
                    if (++requests == 1) {
                        respond("temporary failure", HttpStatusCode.fromValue(status))
                    } else {
                        respondJson(PingResponse("pong"))
                    }
                }) { client ->
                    client.ping().shouldBeRight(PingResponse("pong"))
                    requests shouldBe 2
                }
            }
        }

        "GET requests stop after three retries and retain the final problem details" {
            var requests = 0
            val problem = MshApiProblemDetails(status = 503, title = "Unavailable")
            withClient({ requests++; respondJson(problem, HttpStatusCode.ServiceUnavailable) }) { client ->
                client.ping().shouldBeLeft(EdiAdapterError.Api(503, problem))
                requests shouldBe 4
            }
        }

        "HTTP retry count is configurable and zero disables retries" {
            for (maxRetries in listOf(0, 1)) {
                var requests = 0
                withClient(
                    { requests++; respond("unavailable", HttpStatusCode.ServiceUnavailable) },
                    maxHttpRetries = maxRetries
                ) { client ->
                    client.ping().shouldBeLeft(EdiAdapterError.Api(503))
                    requests shouldBe maxRetries + 1
                }
            }
        }

        "GET requests do not retry other HTTP failures" {
            for (status in listOf(400, 401, 403, 404, 501)) {
                var requests = 0
                withClient({ requests++; respond("failure", HttpStatusCode.fromValue(status)) }) { client ->
                    client.ping().shouldBeLeft(EdiAdapterError.Api(status))
                    requests shouldBe 1
                }
            }
        }

        "GET requests respect Retry-After even above the backoff cap" {
            var requests = 0
            val delays = mutableListOf<Long>()
            withClient(
                {
                    if (++requests == 1) {
                        respond("busy", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "12"))
                    } else {
                        respondJson(PingResponse("pong"))
                    }
                },
                retryDelay = { delays.add(it) }
            ) { client ->
                client.ping().shouldBeRight(PingResponse("pong"))
                requests shouldBe 2
                delays shouldBe listOf(12000L)
            }
        }

        "GET requests retry network failures and retain the last transport error" {
            var requests = 0
            withClient({ throw IOException("Connection lost on attempt ${++requests}") }) { client ->
                val error = client.ping().shouldBeLeft().shouldBeInstanceOf<EdiAdapterError.Transport>()
                requests shouldBe 4
                error.cause.shouldBeInstanceOf<IOException>().message shouldBe "Connection lost on attempt 4"
            }
        }

        "GET requests recover after a request timeout" {
            var requests = 0
            withClient({ request ->
                if (++requests == 1) throw HttpRequestTimeoutException(request)
                respondJson(PingResponse("pong"))
            }) { client ->
                client.ping().shouldBeRight(PingResponse("pong"))
                requests shouldBe 2
            }
        }

        "custom HTTP clients must install the retry plugin" {
            HttpEdiAdapterClient(
                clientProvider = { HttpClient(MockEngine) { engine { addHandler { respondJson(PingResponse("pong")) } } } },
                ediAdapterUrl = "http://localhost"
            ).use { client ->
                shouldThrow<IllegalStateException> { client.ping() }
                    .message!!.contains("RetryFeature") shouldBe true
            }
        }

        "SSL and unexpected failures are not retried" {
            for (failure in listOf(SSLException("TLS failure"), IllegalStateException("Misconfigured"))) {
                var requests = 0
                withClient({ requests++; throw failure }) { client ->
                    client.ping().shouldBeLeft().shouldBeInstanceOf<EdiAdapterError.Transport>()
                    requests shouldBe 1
                }
            }
        }

        "postMessage retries transient HTTP failures with the same payload" {
            val message = PostMessageRequest("PHhtbC8+", 123, listOf(456), "application/xml", "base64", "DIALOG_HELSEFAGLIG")
            for (status in listOf(408, 429, 500, 502, 503, 504)) {
                var requests = 0
                withClient({ request ->
                    request.method shouldBe HttpMethod.Post
                    request.url.fullPath shouldBe "/api/v3/messages"
                    request.body<PostMessageRequest>() shouldBe message
                    if (++requests == 1) {
                        respond("temporary failure", HttpStatusCode.fromValue(status))
                    } else {
                        respondJson(PostMessageResponse(id), HttpStatusCode.Accepted)
                    }
                }) { client ->
                    client.postMessage(message).shouldBeRight(PostMessageResponse(id))
                    requests shouldBe 2
                }
            }
        }

        "postApprec retries transient HTTP failures with the same message id and payload" {
            val apprec = PostAppRecRequest(123, AppRecStatus.OK)
            for (status in listOf(408, 429, 500, 502, 503, 504)) {
                var requests = 0
                withClient({ request ->
                    request.method shouldBe HttpMethod.Post
                    request.url.fullPath shouldBe "/api/v3/messages/$id/apprec"
                    request.body<PostAppRecRequest>() shouldBe apprec
                    if (++requests == 1) {
                        respond("temporary failure", HttpStatusCode.fromValue(status))
                    } else {
                        respondJson(PostApprecResponse(id), HttpStatusCode.Accepted)
                    }
                }) { client ->
                    client.postApprec(id, apprec).shouldBeRight(PostApprecResponse(id))
                    requests shouldBe 2
                }
            }
        }

        "message submissions retry transport failures within the configured limit" {
            val message = PostMessageRequest("PHhtbC8+", 123, listOf(456), "application/xml", "base64", "DIALOG_HELSEFAGLIG")
            val apprec = PostAppRecRequest(123, AppRecStatus.OK)
            for (maxRetries in listOf(0, 1, 3)) {
                var requests = 0
                withClient(
                    { requests++; throw IOException("Connection lost") },
                    maxHttpRetries = maxRetries
                ) { client ->
                    client.postMessage(message).shouldBeLeft().shouldBeInstanceOf<EdiAdapterError.Transport>()
                    requests shouldBe maxRetries + 1
                    client.postApprec(id, apprec).shouldBeLeft().shouldBeInstanceOf<EdiAdapterError.Transport>()
                    requests shouldBe 2 * (maxRetries + 1)
                }
            }
        }

        "message submissions stop at the HTTP retry limit and retain the final problem" {
            val message = PostMessageRequest("PHhtbC8+", 123, listOf(456), "application/xml", "base64", "DIALOG_HELSEFAGLIG")
            val problem = MshApiProblemDetails(status = 503, title = "Unavailable")
            var requests = 0
            withClient({ requests++; respondJson(problem, HttpStatusCode.ServiceUnavailable) }) { client ->
                client.postMessage(message).shouldBeLeft(EdiAdapterError.Api(503, problem))
                requests shouldBe 4
                client.postApprec(id, PostAppRecRequest(123, AppRecStatus.OK)).shouldBeLeft(EdiAdapterError.Api(503, problem))
                requests shouldBe 8
            }
        }

        "message submissions do not retry validation failures" {
            val message = PostMessageRequest("PHhtbC8+", 123, listOf(456), "application/xml", "base64", "DIALOG_HELSEFAGLIG")
            var requests = 0
            withClient({ requests++; respond("invalid", HttpStatusCode.BadRequest) }) { client ->
                client.postMessage(message).shouldBeLeft(EdiAdapterError.Api(400))
                client.postApprec(id, PostAppRecRequest(123, AppRecStatus.OK)).shouldBeLeft(EdiAdapterError.Api(400))
                requests shouldBe 2
            }
        }

        "other write requests do not retry transient HTTP failures" {
            var requests = 0
            withClient({ requests++; respond("unavailable", HttpStatusCode.ServiceUnavailable) }) { client ->
                client.deleteNotifications(DeleteNotificationsRequest(listOf(id))).shouldBeLeft(EdiAdapterError.Api(503))
                client.markMessageAsDownloaded(id, MarkAsDownloadedRequest(123)).shouldBeLeft(EdiAdapterError.Api(503))
                client.setMshConfigurations(
                    SetMshConfigurationsRequest(listOf(MshConfiguration(123, ReceiveNotificationChannel.API)))
                ).shouldBeLeft(EdiAdapterError.Api(503))
                client.deleteMshConfigurations(listOf(123)).shouldBeLeft(EdiAdapterError.Api(503))
                requests shouldBe 4
            }
        }

        "transport failures are returned without retrying writes" {
            val failure = IOException("Connection lost")
            var requests = 0
            withClient({ requests++; throw failure }) { client ->
                val error = client.markMessageAsDownloaded(id, MarkAsDownloadedRequest(123)).shouldBeLeft()
                    .shouldBeInstanceOf<EdiAdapterError.Transport>()
                error.cause.shouldBeInstanceOf<IOException>().message shouldBe failure.message
                requests shouldBe 1
            }
        }

        "cancellation propagates" {
            val failure = CancellationException("Cancelled")
            var requests = 0
            withClient({ requests++; throw failure }) { client ->
                shouldThrow<CancellationException> { client.ping() }.message shouldBe failure.message
                requests shouldBe 1
            }
        }

        "cancellation during backoff prevents another attempt" {
            withTimeout(5000.milliseconds) {
                coroutineScope {
                    val waiting = CompletableDeferred<Unit>()
                    var requests = 0
                    withClient(
                        { requests++; respond("busy", HttpStatusCode.ServiceUnavailable) },
                        retryDelay = { waiting.complete(Unit); awaitCancellation() }
                    ) { client ->
                        val call = this@coroutineScope.launch { client.ping() }
                        waiting.await()
                        call.cancelAndJoin()
                        call.isCancelled shouldBe true
                        requests shouldBe 1
                    }
                }
            }
        }
    }
)

private inline fun <reified T> HttpRequestData.body(): T = Json.decodeFromString((body as TextContent).text)

private suspend fun withClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    maxHttpRetries: Int = 3,
    retryDelay: suspend (Long) -> Unit = {},
    block: suspend (EdiAdapterClient) -> Unit
) {
    HttpEdiAdapterClient(
        clientProvider = {
            HttpClient(MockEngine) {
                engine { addHandler(handler) }
                expectSuccess = false
                install(HttpRequestRetry) {
                    noRetry()
                    delay { retryDelay(it) }
                }
            }
        },
        ediAdapterUrl = "http://localhost/",
        maxHttpRetries = maxHttpRetries
    )
        .use { block(it) }
}

private inline fun <reified T> MockRequestHandleScope.respondJson(
    body: T,
    status: HttpStatusCode = HttpStatusCode.OK
): HttpResponseData = respond(Json.encodeToString(body), status, headersOf(HttpHeaders.ContentType, "application/json"))
