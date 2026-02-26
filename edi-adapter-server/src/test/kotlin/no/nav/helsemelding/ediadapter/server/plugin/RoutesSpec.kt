package no.nav.helsemelding.ediadapter.server.plugin

import com.nimbusds.jwt.SignedJWT
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpHeaders.Location
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.HttpStatusCode.Companion.UnsupportedMediaType
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.Route
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.TestApplicationBuilder
import io.ktor.server.testing.testApplication
import no.nav.helsemelding.ediadapter.model.ErrorMessage
import no.nav.helsemelding.ediadapter.model.Metadata
import no.nav.helsemelding.ediadapter.server.auth.AuthConfig.Companion.getTokenSupportConfig
import no.nav.helsemelding.ediadapter.server.config
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.v3.tokenValidationSupport
import kotlin.io.encoding.Base64
import kotlin.text.Charsets.UTF_8
import kotlin.uuid.Uuid
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import kotlinx.serialization.json.Json as JsonUtil

private const val MESSAGE1 = "https://example.com/messages/1"
private const val ROOT_V2 = "/api/v2"

class RoutesSpec : StringSpec(
    {
        lateinit var mockOAuth2Server: MockOAuth2Server

        val getToken: (String) -> SignedJWT = { audience: String ->
            mockOAuth2Server.issueToken(
                issuerId = config().azureAuth.issuer.value,
                audience = audience,
                subject = "testUser"
            )
        }

        val invalidAudience = "api://dev-fss.helsemelding.some-other-service/.default"

        beforeSpec {
            mockOAuth2Server = MockOAuth2Server().also { it.start(port = 3344) }
        }

        "GET /messages with single receiver her id returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1"
                respond("""[{"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages with multiple receiver her ids returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1&ReceiverHerIds=2"
                respond("""[{"id":"100", "receiverHerId": "1"}, {"id":"200", "receiverHerId": "2"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&receiverHerIds=2")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}, {"id":"200", "receiverHerId": "2"}]"""
            }
        }

        "GET /messages with receiver her id and sender her id returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1&SenderHerId=2"
                respond("""[{"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&senderHerId=2")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages with receiver her id and business document id returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1&BusinessDocumentId=10"
                respond("""[{"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&businessDocumentId=10")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages with receiver her id and messages to fetch returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1&MessagesToFetch=1"
                respond("""[{"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&messagesToFetch=1")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages with receiver her id and messages to fetch (0) returns error" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)
                client = createJsonEnabledClient()

                val response = client.get("/api/v1/messages?receiverHerIds=1&messagesToFetch=0")

                response.status shouldBe BadRequest

                val errorMessage = response.body<ErrorMessage>()

                errorMessage.error shouldBe "Messages to fetch must be a number between 1 and 100"
                errorMessage.errorCode shouldBe 400
                errorMessage.requestId shouldBe "unknown"
                errorMessage.stackTrace shouldBe null
                errorMessage.validationErrors shouldBe null
            }
        }

        "GET /messages with receiver her id and messages to fetch (101) returns error" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)
                client = createJsonEnabledClient()

                val response = client.get("/api/v1/messages?receiverHerIds=1&messagesToFetch=101")

                response.status shouldBe BadRequest
                val errorMessage = response.body<ErrorMessage>()

                errorMessage.error shouldBe "Messages to fetch must be a number between 1 and 100"
                errorMessage.errorCode shouldBe 400
                errorMessage.requestId shouldBe "unknown"
                errorMessage.stackTrace shouldBe null
                errorMessage.validationErrors shouldBe null
            }
        }

        "GET /messages with receiver her id and order by ASC returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1&OrderBy=1"
                respond("""[{"id":"100", "receiverHerId": "1"}, {"id":"101", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&orderBy=1")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}, {"id":"101", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages with receiver her id and order by DESC returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1&OrderBy=2"
                respond("""[{"id":"101", "receiverHerId": "1"}, {"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&orderBy=2")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"101", "receiverHerId": "1"}, {"id":"100", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages with receiver her id and order by non valid sorting returns error" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)
                client = createJsonEnabledClient()

                val response = client.get("/api/v1/messages?receiverHerIds=1&orderBy=3")

                response.status shouldBe BadRequest
                val errorMessage = response.body<ErrorMessage>()

                errorMessage.error shouldBe "Order by must be 1 (Ascending) or 2 (Descending)"
                errorMessage.errorCode shouldBe 400
                errorMessage.requestId shouldBe "unknown"
                errorMessage.stackTrace shouldBe null
                errorMessage.validationErrors shouldBe null
            }
        }

        "GET /messages with receiver her id and include metadata returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1&IncludeMetadata=true"
                respond(
                    """{
                    "id": "100",
                    "contentType": "application/xml",
                    "receiverHerId": 1,
                    "senderHerId": 2,
                    "businessDocumentId": "10",
                    "businessDocumentGenDate": "2008-11-26T19:31:17.281+00:00",
                    "isAppRec": false,
                    "sourceSystem": "helsemelding EDI 2.0 edi-adapter, v1.0"
                }"""
                )
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=1&includeMetadata=true")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """{
                    "id": "100",
                    "contentType": "application/xml",
                    "receiverHerId": 1,
                    "senderHerId": 2,
                    "businessDocumentId": "10",
                    "businessDocumentGenDate": "2008-11-26T19:31:17.281+00:00",
                    "isAppRec": false,
                    "sourceSystem": "helsemelding EDI 2.0 edi-adapter, v1.0"
                }"""
            }
        }

        "GET /messages with receiver her id and non valid include metadata returns error" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)
                client = createJsonEnabledClient()

                val response = client.get("/api/v1/messages?receiverHerIds=1&includeMetadata=foobar")

                response.status shouldBe BadRequest
                val errorMessage = response.body<ErrorMessage>()

                errorMessage.error shouldBe "Include metadata must be 'true' or 'false'"
                errorMessage.errorCode shouldBe 400
                errorMessage.requestId shouldBe "unknown"
                errorMessage.stackTrace shouldBe null
                errorMessage.validationErrors shouldBe null
            }
        }

        "GET /messages with blank receiver her id returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages?receiverHerIds=")
                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Receiver her ids"
            }
        }

        "GET /messages without receiver her id returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages")

                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Receiver her ids"
            }
        }

        "GET /messages/{id} returns EDI response" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages/42"
                respond(
                    """{
                     "id": "42",
                     "contentType": "application/xml",
                     "receiverHerId": 1,
                     "senderHerId": 2,
                     "businessDocumentId": "100",
                     "businessDocumentGenDate": "2008-11-26T19:31:17.281+00:00",
                     "isAppRec": false,
                     "sourceSystem": "helsemelding EDI 2.0 edi-adapter, v1.0"
                    }"""
                )
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/42")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """{
                     "id": "42",
                     "contentType": "application/xml",
                     "receiverHerId": 1,
                     "senderHerId": 2,
                     "businessDocumentId": "100",
                     "businessDocumentGenDate": "2008-11-26T19:31:17.281+00:00",
                     "isAppRec": false,
                     "sourceSystem": "helsemelding EDI 2.0 edi-adapter, v1.0"
                    }"""
            }
        }

        "GET /messages/{id} with blank message id returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/%20")

                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Message id"
            }
        }

        "GET /messages/{id} missing message id returns 404" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/")

                response.status shouldBe NotFound
            }
        }

        "GET /messages/{id}/document returns EDI response" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages/99/business-document"
                respond("<xml>doc</xml>")
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/99/document")

                response.status shouldBe OK
                response.bodyAsText() shouldBe "<xml>doc</xml>"
            }
        }

        "GET /messages/{id}/status returns EDI response" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages/55/status"
                respond(
                    """[
                         {
                           "receiverHerId": 1,
                           "transportDeliveryState": "Acknowledged",
                           "sent": true,
                           "appRecStatus": null
                         }
                       ]"""
                )
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/55/status")

                response.status shouldBe OK
                response.bodyAsText() shouldBe
                    """[
                         {
                           "receiverHerId": 1,
                           "transportDeliveryState": "Acknowledged",
                           "sent": true,
                           "appRecStatus": null
                         }
                       ]"""
            }
        }

        "GET /messages/{id}/apprec returns EDI response" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages/10/apprec"
                respond(
                    """[
                        { "receiverHerId": 1,
                          "appRecStatus": Ok,
                          "appRecErrorList": null
                        }
                      ]"""
                )
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.get("/api/v1/messages/10/apprec")

                response.status shouldBe OK
                response.bodyAsText() shouldBe
                    """[
                        { "receiverHerId": 1,
                          "appRecStatus": Ok,
                          "appRecErrorList": null
                        }
                      ]"""
            }
        }

        "POST /messages returns metadata (id and location) from EDI response" {
            val newLocation = MESSAGE1
            val newUuid = Uuid.random()
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages"
                (request.body as TextContent).text shouldContain base64EncodedDocument()
                respond(
                    content = JsonUtil.encodeToString(newUuid.toString()),
                    headers = headersOf(Location, newLocation),
                    status = Created
                )
            }

            testApplication {
                installExternalRoutes(ediClient)
                client = createJsonEnabledClient()

                val message =
                    """
                {
                  "businessDocument":  ${base64EncodedDocument()},
                  "contentType": "application/xml",
                  "contentTransferEncoding": "base64",
                  "ebXmlOverrides": {
                    "cpaId": "test-cpa-id",
                    "conversationId": "test-conversation-id",
                    "service": "test-service",
                    "serviceType": "test-service-type",
                    "action": "test-action",
                    "role": "test-sender-role",
                    "useSenderLevel1HerId": true,
                    "receiverRole": "test-receiver-role",
                    "applicationName": "test-application-name",
                    "applicationVersion": "1.0",
                    "middlewareName": "test-middleware-name",
                    "middlewareVersion": "1.0",
                    "compressPayload": false
                  },
                  "receiverHerIdsSubset": [123456]
                }
                """

                val response = client.post("/api/v1/messages") {
                    contentType(Json)
                    setBody(message)
                }

                response.status shouldBe Created
                val metadata = response.body<Metadata>()
                metadata.id shouldBe newUuid
                metadata.location shouldBe newLocation
            }
        }

        "POST /messages with empty body returns 415" {
            testApplication {
                installExternalRoutes(fakeEdiClient { error("Should not be called") })

                val response = client.post("/api/v1/messages") {
                    contentType(Json)
                    setBody("")
                }

                response.status shouldBe UnsupportedMediaType
            }
        }

        "POST /messages without body returns 415" {
            testApplication {
                installExternalRoutes(fakeEdiClient { error("Should not be called") })

                val response = client.post("/api/v1/messages")

                response.status shouldBe UnsupportedMediaType
            }
        }

        "POST /messages with invalid body (json) returns 400" {
            testApplication {
                installExternalRoutes(fakeEdiClient { error("Should not be called") })

                val response = client.post("/api/v1/messages") {
                    contentType(Json)
                    setBody("{ not-valid-json }")
                }

                response.status shouldBe BadRequest
            }
        }

        "POST /messages returns 500 on unexpected exception" {
            val ediClient = fakeEdiClient { throw RuntimeException("boom") }

            testApplication {
                installExternalRoutes(ediClient)
                client = createJsonEnabledClient()

                val response = client.post("/api/v1/messages") {
                    contentType(Json)
                    setBody(
                        """{
                            "businessDocument":  ${base64EncodedDocument()},
                            "contentType": "application/xml",
                            "contentTransferEncoding": "base64"
                        }"""
                    )
                }

                response.status shouldBe InternalServerError
                val errorMessage = response.body<ErrorMessage>()

                errorMessage.error shouldBe InternalServerError.description
                errorMessage.errorCode shouldBe 500
                errorMessage.requestId shouldBe "unknown"
                errorMessage.stackTrace shouldBe null
                errorMessage.validationErrors shouldBe null
            }
        }

        "POST /messages/{id}/apprec/{sender} returns metadata (id and location) from EDI response" {
            val newLocation = MESSAGE1
            val newUuid = Uuid.random()
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages/77/apprec/8142"
                (request.body as TextContent).text shouldContain "1"
                respond(
                    content = JsonUtil.encodeToString(newUuid.toString()),
                    headers = headersOf(Location, newLocation),
                    status = Created
                )
            }

            testApplication {
                installExternalRoutes(ediClient)
                client = createJsonEnabledClient()

                val apprecBody = """{ "appRecStatus":"1", "appRecErrorList":[] }"""

                val response = client.post("/api/v1/messages/77/apprec/8142") {
                    contentType(Json)
                    setBody(apprecBody)
                }

                response.status shouldBe Created
                val metadata = response.body<Metadata>()
                metadata.id shouldBe newUuid
                metadata.location shouldBe newLocation
            }
        }

        "POST /messages/{id}/apprec/{sender} with blank sender returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.post("/api/v1/messages/77/apprec/%20") {
                    contentType(Json)
                    setBody("""{ "appRecStatus":"1", "appRecErrorList":[] }""")
                }

                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Sender"
            }
        }

        "POST /messages/{id}/apprec missing sender returns 404" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.post("/api/v1/messages/77/apprec/") {
                    contentType(Json)
                    setBody("""{"status":"1"}""")
                }
                response.status shouldBe NotFound
            }
        }

        "PUT /messages/{id}/read/{herId} marks message as read" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/Messages/5/read/111"
                respond("", status = NoContent)
            }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.put("/api/v1/messages/5/read/111")

                response.status shouldBe NoContent
            }
        }

        "PUT /messages/{id}/read/{herId} with blank herId returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.put("/api/v1/messages/5/read/%20")

                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Her id"
            }
        }

        "PUT /messages/{id}/read missing herId returns 404" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient)

                val response = client.put("/api/v1/messages/5/read/")

                response.status shouldBe NotFound
            }
        }

        "GET /messages returns EDI response with authentication" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages?ReceiverHerIds=1"
                respond("""[{"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClient, useAuthentication = true)

                val response = client.getWithAuth("/api/v1/messages?receiverHerIds=1", getToken)

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages returns Unauthorised if access token is missing" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient, useAuthentication = true)

                val response = client.get("/api/v1/messages?id=1")

                response.status shouldBe Unauthorized
            }
        }

        "GET /messages returns Unauthorised if access token is invalid" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClient, useAuthentication = true)

                val response = client.getWithAuth("/api/v1/messages?id=1", getToken, invalidAudience)

                response.status shouldBe Unauthorized
            }
        }

        "GET /messages/notices with single receiver her id returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages/notices?ReceiverHerIds=1"
                respond(
                    """[{
                    "id": "100",
                    "noticeType": "RefusedMessage",
                    "contentType": "application/xml",
                    "receiverHerId": 1,
                    "senderHerId": 2,
                    "businessDocumentId": "10",
                    "businessDocumentGenDate": "2008-11-26T19:31:17.281+00:00",
                    "isAppRec": false,
                    "sourceSystem": "helsemelding EDI 2.0 edi-adapter, v1.0",
                    "refusedReason": "Receiver does not support the message type: ABC"
                }]"""
                )
            }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClient)

                val response = client.get("$ROOT_V2/messages/notices?receiverHerIds=1")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{
                    "id": "100",
                    "noticeType": "RefusedMessage",
                    "contentType": "application/xml",
                    "receiverHerId": 1,
                    "senderHerId": 2,
                    "businessDocumentId": "10",
                    "businessDocumentGenDate": "2008-11-26T19:31:17.281+00:00",
                    "isAppRec": false,
                    "sourceSystem": "helsemelding EDI 2.0 edi-adapter, v1.0",
                    "refusedReason": "Receiver does not support the message type: ABC"
                }]"""
            }
        }

        "GET /messages/notices with multiple receiver her ids returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages/notices?ReceiverHerIds=1&ReceiverHerIds=2"
                respond(
                    """[
                    {"id":"100", "noticeType": "NewMessage", "receiverHerId": "1"}, 
                    {"id":"200", "noticeType": "RefusedMessage", "receiverHerId": "2"}
                ]"""
                )
            }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClient)

                val response = client.get("$ROOT_V2/messages/notices?receiverHerIds=1&receiverHerIds=2")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[
                    {"id":"100", "noticeType": "NewMessage", "receiverHerId": "1"}, 
                    {"id":"200", "noticeType": "RefusedMessage", "receiverHerId": "2"}
                ]"""
            }
        }

        "GET /messages/notices with receiver her id and message notices to fetch returns EDI response" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages/notices?ReceiverHerIds=1&MessagesToFetch=1"
                respond("""[{"id":"100", "noticeType": "NewMessage", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClient)

                val response = client.get("$ROOT_V2/messages/notices?receiverHerIds=1&messagesToFetch=1")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "noticeType": "NewMessage", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages/notices with receiver her id and message notices to fetch (0) returns error" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClient)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V2/messages/notices?receiverHerIds=1&messagesToFetch=0")

                response.status shouldBe BadRequest

                val errorMessage = response.body<ErrorMessage>()

                errorMessage.error shouldBe "Messages to fetch must be a number between 1 and 100"
                errorMessage.errorCode shouldBe 400
                errorMessage.requestId shouldBe "unknown"
                errorMessage.stackTrace shouldBe null
                errorMessage.validationErrors shouldBe null
            }
        }

        "GET /messages/notices with receiver her id and message notices to fetch (101) returns error" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClient)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V2/messages/notices?receiverHerIds=1&messagesToFetch=101")

                response.status shouldBe BadRequest
                val errorMessage = response.body<ErrorMessage>()

                errorMessage.error shouldBe "Messages to fetch must be a number between 1 and 100"
                errorMessage.errorCode shouldBe 400
                errorMessage.requestId shouldBe "unknown"
                errorMessage.stackTrace shouldBe null
                errorMessage.validationErrors shouldBe null
            }
        }

        "GET /messages/notices with blank receiver her id returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClient)

                val response = client.get("$ROOT_V2/messages/notices?receiverHerIds=")
                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Receiver her ids"
            }
        }

        "GET /messages/notices without receiver her id returns 400" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClient)

                val response = client.get("$ROOT_V2/messages/notices")

                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Receiver her ids"
            }
        }

        "GET /messages/notices returns EDI response with authentication" {
            val ediClient = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages/notices?ReceiverHerIds=1"
                respond("""[{"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClient, useAuthentication = true)

                val response = client.getWithAuth("$ROOT_V2/messages/notices?receiverHerIds=1", getToken)

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages/notices returns Unauthorised if access token is missing" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClient, useAuthentication = true)

                val response = client.get("$ROOT_V2/messages/notices?id=1")

                response.status shouldBe Unauthorized
            }
        }

        "GET /messages/notices returns Unauthorised if access token is invalid" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClient, useAuthentication = true)

                val response = client.getWithAuth("$ROOT_V2/messages/notices?id=1", getToken, invalidAudience)

                response.status shouldBe Unauthorized
            }
        }

        "POST /mshConfiguration returns 204 no content from EDI response" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/MshConfiguration"
                respond(
                    content = "",
                    status = NoContent
                )
            }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClient)
                client = createJsonEnabledClient()

                val body =
                    """
                {
                  "mshConfigurations": [
                    {
                      "herId": 123456,
                      "receiveNotificationChannel": "ApiPolling",
                      "receiveRefusedMessageNotices": true,
                      "rejectMessageFilters": {
                        "MessageFunction": [
                          "string"
                        ],
                        "XmlNamespace": [
                          "string"
                        ]
                      }
                    }
                  ]
                }
                """

                val response = client.post("$ROOT_V2/mshConfiguration") {
                    contentType(Json)
                    setBody(body)
                }

                response.status shouldBe NoContent
                response.bodyAsText() shouldBe ""
            }
        }

        "POST /mshConfiguration with empty body returns 415" {
            testApplication {
                installExternalRoutes(ediClientV2 = fakeEdiClient { error("Should not be called") })

                val response = client.post("$ROOT_V2/mshConfiguration") {
                    contentType(Json)
                    setBody("")
                }

                response.status shouldBe UnsupportedMediaType
            }
        }

        "POST /mshConfiguration without body returns 415" {
            testApplication {
                installExternalRoutes(ediClientV2 = fakeEdiClient { error("Should not be called") })

                val response = client.post("$ROOT_V2/mshConfiguration")

                response.status shouldBe UnsupportedMediaType
            }
        }

        "POST /mshConfiguration with invalid body (json) returns 400" {
            testApplication {
                installExternalRoutes(ediClientV2 = fakeEdiClient { error("Should not be called") })

                val response = client.post("$ROOT_V2/mshConfiguration") {
                    contentType(Json)
                    setBody("{ not-valid-json }")
                }

                response.status shouldBe BadRequest
            }
        }

        "POST /mshConfiguration with invalid receiveNotificationChannel returns 400" {
            testApplication {
                installExternalRoutes(ediClientV2 = fakeEdiClient { error("Should not be called") })
                client = createJsonEnabledClient()

                val body =
                    """
                {
                  "mshConfigurations": [
                    {
                      "herId": 123456,
                      "receiveNotificationChannel": "ABC",
                      "receiveRefusedMessageNotices": true,
                      "rejectMessageFilters": {
                        "MessageFunction": [
                          "string"
                        ],
                        "XmlNamespace": [
                          "string"
                        ]
                      }
                    }
                  ]
                }
                """

                val response = client.post("$ROOT_V2/mshConfiguration") {
                    contentType(Json)
                    setBody(body)
                }

                response.status shouldBe BadRequest
            }
        }

        "POST /mshConfiguration returns 500 on unexpected exception" {
            val ediClient = fakeEdiClient { throw RuntimeException("boom") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClient)
                client = createJsonEnabledClient()

                val response = client.post("$ROOT_V2/mshConfiguration") {
                    contentType(Json)
                    setBody(
                        """
                    {
                      "mshConfigurations": [
                        {
                          "herId": 123456,
                          "receiveNotificationChannel": "ApiPolling",
                          "receiveRefusedMessageNotices": true,
                          "rejectMessageFilters": {
                            "MessageFunction": [
                              "string"
                            ],
                            "XmlNamespace": [
                              "string"
                            ]
                          }
                        }
                      ]
                    }
                        """
                    )
                }

                response.status shouldBe InternalServerError
                val errorMessage = response.body<ErrorMessage>()

                errorMessage.error shouldBe InternalServerError.description
                errorMessage.errorCode shouldBe 500
                errorMessage.requestId shouldBe "unknown"
                errorMessage.stackTrace shouldBe null
                errorMessage.validationErrors shouldBe null
            }
        }

        "POST /mshConfiguration returns EDI response with authentication" {
            val ediClient = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/MshConfiguration"
                respond(
                    content = "",
                    status = NoContent
                )
            }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClient, useAuthentication = true)
                client = createJsonEnabledClient()

                val body =
                    """
                {
                  "mshConfigurations": [
                    {
                      "herId": 123456,
                      "receiveNotificationChannel": "ApiPolling",
                      "receiveRefusedMessageNotices": true
                    }
                  ]
                }
                """

                val response = client.postWithAuth("$ROOT_V2/mshConfiguration", getToken) {
                    contentType(Json)
                    setBody(body)
                }

                response.status shouldBe NoContent
                response.bodyAsText() shouldBe ""
            }
        }

        "POST /mshConfiguration returns Unauthorised if access token is missing" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClient, useAuthentication = true)
                client = createJsonEnabledClient()

                val body =
                    """
                {
                  "mshConfigurations": [
                    {
                      "herId": 123456,
                      "receiveNotificationChannel": "ApiPolling",
                      "receiveRefusedMessageNotices": true
                    }
                  ]
                }
                """

                val response = client.post("$ROOT_V2/mshConfiguration") {
                    contentType(Json)
                    setBody(body)
                }

                response.status shouldBe Unauthorized
            }
        }

        "POST /mshConfiguration returns Unauthorised if access token is invalid" {
            val ediClient = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClient, useAuthentication = true)
                client = createJsonEnabledClient()

                val body =
                    """
                {
                  "mshConfigurations": [
                    {
                      "herId": 123456,
                      "receiveNotificationChannel": "ApiPolling",
                      "receiveRefusedMessageNotices": true
                    }
                  ]
                }
                """

                val response = client.postWithAuth("$ROOT_V2/mshConfiguration", getToken, invalidAudience) {
                    contentType(Json)
                    setBody(body)
                }

                response.status shouldBe Unauthorized
            }
        }
    }
)

private fun ApplicationTestBuilder.createJsonEnabledClient(): HttpClient =
    createClient {
        install(ClientContentNegotiation) {
            json()
        }
    }

private fun TestApplicationBuilder.installExternalRoutes(
    ediClient: HttpClient = fakeEdiClient { error("Should not be called") },
    ediClientV2: HttpClient = fakeEdiClient { error("Should not be called") },
    useAuthentication: Boolean = false
) {
    install(ContentNegotiation) {
        json()
    }

    val issuer = config().azureAuth.issuer.value

    if (useAuthentication) {
        install(Authentication) {
            tokenValidationSupport(
                issuer,
                getTokenSupportConfig()
            )
        }
    }

    routing {
        val externalRoutes: Route.() -> Unit = { externalRoutes(ediClient, ediClientV2) }

        if (useAuthentication) {
            authenticate(issuer, build = externalRoutes)
        } else {
            externalRoutes(this)
        }
    }
}

private fun fakeEdiClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): HttpClient =
    HttpClient(MockEngine) {
        engine {
            addHandler(handler)
        }

        install(ClientContentNegotiation) {
            json()
        }
    }

private suspend fun HttpClient.getWithAuth(
    url: String,
    getToken: (String) -> SignedJWT,
    audience: String = config().azureAuth.appScope.value
): HttpResponse =
    get(url) {
        header(
            Authorization,
            "Bearer ${getToken(audience).serialize()}"
        )
    }

private suspend fun HttpClient.postWithAuth(
    url: String,
    getToken: (String) -> SignedJWT,
    audience: String = config().azureAuth.appScope.value,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse =
    post(url) {
        header(
            Authorization,
            "Bearer ${getToken(audience).serialize()}"
        )
        block()
    }

private fun base64EncodedDocument(): String =
    Base64.encode(
        """
            <MsgHead>
                <Body>hello world</Body>
            </MsgHead>
        """
            .trimIndent()
            .toByteArray(UTF_8)
    )
