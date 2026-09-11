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
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helsemelding.ediadapter.model.common.GetBusinessDocumentResponse
import no.nav.helsemelding.ediadapter.model.v3.AppRecStatus
import no.nav.helsemelding.ediadapter.model.v3.GetMessageResponse
import no.nav.helsemelding.ediadapter.model.v3.GetNotificationsResponse
import no.nav.helsemelding.ediadapter.model.v3.GetStatusResponse
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
import java.io.IOException
import kotlin.uuid.Uuid

class EdiAdapterClientSpec : StringSpec(
    {
        val id = Uuid.random()

        "getNotifications sends her ids, offset and notifications to fetch" {
            val expected = GetNotificationsResponse(
                listOf(
                    Notification(
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

        "getNotifications omits the default page size" {
            withClient({ request ->
                request.url.fullPath shouldBe "/api/v3/notifications?herIds=123&offset=0"
                respondJson(GetNotificationsResponse(emptyList()))
            }) { client ->
                client.getNotifications(listOf(123), 0).shouldBeRight(GetNotificationsResponse(emptyList()))
            }
        }

        "postMessage sends the V3 payload and decodes an accepted response" {
            val body = PostMessageRequest("PHhtbC8+", 123, listOf(456), "application/xml", "base64", "DIALOG_HELSEFAGLIG")
            withClient({ request ->
                request.method shouldBe HttpMethod.Post
                request.url.fullPath shouldBe "/api/v3/messages"
                request.body.shouldBeInstanceOf<TextContent>().contentType.toString() shouldBe "application/json"
                request.body<PostMessageRequest>() shouldBe body
                respondJson(PostMessageResponse(id.toString()), HttpStatusCode.Accepted)
            }) { client ->
                client.postMessage(body).shouldBeRight(PostMessageResponse(id.toString()))
            }
        }

        "getMessage returns V3 metadata" {
            val expected = GetMessageResponse(id.toString(), senderHerId = 123, receiverHerIds = listOf(456))
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
            val expected = GetStatusResponse(emptyList())
            withClient({ request ->
                request.method shouldBe HttpMethod.Get
                request.url.fullPath shouldBe "/api/v3/messages/$id/status"
                respondJson(expected)
            }) { client -> client.getMessageStatus(id).shouldBeRight(expected) }
        }

        "postApprec sends the sender in the V3 payload" {
            val body = PostAppRecRequest(123, AppRecStatus.OK, applicationName = "app", applicationVersion = "1.0")
            withClient({ request ->
                request.method shouldBe HttpMethod.Post
                request.url.fullPath shouldBe "/api/v3/messages/$id/apprec"
                request.body<PostAppRecRequest>() shouldBe body
                respondJson(PostApprecResponse(id.toString()), HttpStatusCode.Accepted)
            }) { client -> client.postApprec(id, body).shouldBeRight(PostApprecResponse(id.toString())) }
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
            withClient({ respond("not JSON") }) { client ->
                client.ping().shouldBeLeft().shouldBeInstanceOf<EdiAdapterError.Decoding>()
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
            withClient({ throw failure }) { client ->
                shouldThrow<CancellationException> { client.ping() }.message shouldBe failure.message
            }
        }
    }
)

private inline fun <reified T> HttpRequestData.body(): T = Json.decodeFromString((body as TextContent).text)

private suspend fun withClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    block: suspend (EdiAdapterClient) -> Unit
) {
    HttpEdiAdapterClient(
        clientProvider = { HttpClient(MockEngine) { engine { addHandler(handler) }; expectSuccess = false } },
        ediAdapterUrl = "http://localhost/"
    )
        .use { block(it) }
}

private inline fun <reified T> MockRequestHandleScope.respondJson(
    body: T,
    status: HttpStatusCode = HttpStatusCode.OK
): HttpResponseData = respond(Json.encodeToString(body), status, headersOf(HttpHeaders.ContentType, "application/json"))
