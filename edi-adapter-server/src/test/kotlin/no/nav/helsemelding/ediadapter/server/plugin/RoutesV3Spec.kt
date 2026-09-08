package no.nav.helsemelding.ediadapter.server.plugin

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Accept
import io.ktor.http.HttpHeaders.Location
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode.Companion.Accepted
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.Locked
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.UnsupportedMediaType
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import no.nav.helsemelding.ediadapter.model.v3.GetNotificationsResponse
import no.nav.helsemelding.ediadapter.model.v3.GetStatusResponse
import no.nav.helsemelding.ediadapter.model.v3.MshApiProblemDetails
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import io.ktor.http.HttpHeaders.ContentType as ContentTypeHeader
import kotlinx.serialization.json.Json as JsonUtil

private const val ROOT_V3 = "/api/v3"
private const val V3_MESSAGE_ID = "70104e29-d573-4578-9ac7-1383410fffc3"
private val jsonHeaders = headersOf(ContentTypeHeader, "application/json")

class RoutesV3Spec : StringSpec(
    {
        "GET /notifications forwards her ids, offset and page size" {
            val payload =
                """{
                    "notifications": [
                        {
                            "relatedMessageId": "$V3_MESSAGE_ID",
                            "type": "MessageDeliveryStateUpdated",
                            "notificationReceiverHerId": 42,
                            "offset": 4294967297
                        }
                    ]
                }"""

            val ediClientV3 = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/notifications?HerIds=42&HerIds=1337&Offset=4294967296&NotificationsToFetch=1000"
                respond(payload, headers = jsonHeaders)
            }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response =
                    client.get("$ROOT_V3/notifications?herIds=42&herIds=1337&offset=4294967296&notificationsToFetch=1000")

                response.status shouldBe OK
                response.bodyAsText() shouldBe payload
                response.body<GetNotificationsResponse>().notifications.single().offset shouldBe 4294967297L
            }
        }

        "GET /notifications with offset zero uses default page size" {
            val ediClientV3 = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/notifications?HerIds=42&Offset=0"
                respond("""{"notifications":[]}""", headers = jsonHeaders)
            }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)

                val response = client.get("$ROOT_V3/notifications?herIds=42&offset=0")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """{"notifications":[]}"""
            }
        }

        "GET /notifications without her ids returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V3/notifications?offset=0")

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
                problem.instance shouldBe "$ROOT_V3/notifications"
            }
        }

        "GET /notifications with blank her id returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V3/notifications?herIds=&offset=0")

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
                problem.instance shouldBe "$ROOT_V3/notifications"
            }
        }

        "GET /notifications with non-numeric her id returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V3/notifications?herIds=invalid&offset=0")

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
                problem.instance shouldBe "$ROOT_V3/notifications"
            }
        }

        "GET /notifications with her id exceeding Int range returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V3/notifications?herIds=2147483648&offset=0")

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
                problem.instance shouldBe "$ROOT_V3/notifications"
            }
        }

        "GET /notifications with duplicate her ids returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V3/notifications?herIds=42&herIds=42&offset=0")

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
                problem.instance shouldBe "$ROOT_V3/notifications"
            }
        }

        "GET /notifications without offset returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V3/notifications?herIds=42")

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
                problem.instance shouldBe "$ROOT_V3/notifications"
            }
        }

        "GET /notifications with blank offset returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V3/notifications?herIds=42&offset=")

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
                problem.instance shouldBe "$ROOT_V3/notifications"
            }
        }

        "GET /notifications with negative offset returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V3/notifications?herIds=42&offset=-1")

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
                problem.instance shouldBe "$ROOT_V3/notifications"
            }
        }

        "GET /notifications with non-numeric offset returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V3/notifications?herIds=42&offset=invalid")

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
                problem.instance shouldBe "$ROOT_V3/notifications"
            }
        }

        "GET /notifications with offset exceeding Long range returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V3/notifications?herIds=42&offset=9223372036854775808")

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
                problem.instance shouldBe "$ROOT_V3/notifications"
            }
        }

        "GET /notifications with notifications to fetch (0) returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V3/notifications?herIds=42&offset=0&notificationsToFetch=0")

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
                problem.instance shouldBe "$ROOT_V3/notifications"
            }
        }

        "GET /notifications with notifications to fetch (1001) returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V3/notifications?herIds=42&offset=0&notificationsToFetch=1001")

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
                problem.instance shouldBe "$ROOT_V3/notifications"
            }
        }

        "GET /notifications with non-numeric notifications to fetch returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V3/notifications?herIds=42&offset=0&notificationsToFetch=invalid")

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
                problem.instance shouldBe "$ROOT_V3/notifications"
            }
        }

        "GET /notifications with blank notifications to fetch returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V3/notifications?herIds=42&offset=0&notificationsToFetch=")

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
                problem.instance shouldBe "$ROOT_V3/notifications"
            }
        }

        "GET /notifications with more than 1500 her ids returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)

                val query = (1..1501).joinToString("&") { id -> "herIds=$id" }
                val response = client.get("$ROOT_V3/notifications?$query&offset=0")

                response.status shouldBe BadRequest
            }
        }

        "GET /messages/{id} returns EDI response" {
            val payload =
                """{
                    "id": "$V3_MESSAGE_ID",
                    "receiverHerIds": [
                        42,
                        1337
                    ],
                    "businessDocumentMsgType": "SVAR_RTG"
                }"""

            val ediClientV3 = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/messages/$V3_MESSAGE_ID"
                request.method shouldBe HttpMethod.Get
                respond(payload, headers = jsonHeaders)
            }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)

                val response = client.get("$ROOT_V3/messages/$V3_MESSAGE_ID")

                response.status shouldBe OK
                response.bodyAsText() shouldBe payload
            }
        }

        "GET /messages/{id}/document returns EDI response" {
            val payload =
                """{
                    "businessDocument": "PHhtbC8+",
                    "contentType": "application/xml",
                    "contentTransferEncoding": "base64"
                }"""

            val ediClientV3 = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/messages/$V3_MESSAGE_ID/business-document"
                request.method shouldBe HttpMethod.Get
                respond(payload, headers = jsonHeaders)
            }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)

                val response = client.get("$ROOT_V3/messages/$V3_MESSAGE_ID/document")

                response.status shouldBe OK
                response.bodyAsText() shouldBe payload
            }
        }

        "GET /messages/{id}/status returns EDI response" {
            val payload =
                """{
                    "statusList": [
                        {
                            "receiverHerId": 42,
                            "transportDeliveryState": "Abandoned",
                            "sent": true,
                            "apprecInfo": {
                                "appRecStatus": "Rejected",
                                "appRecErrorList": [
                                    {
                                        "errorCode": "E10",
                                        "details": null
                                    }
                                ]
                            }
                        }
                    ]
                }"""

            val ediClientV3 = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/messages/$V3_MESSAGE_ID/status"
                request.method shouldBe HttpMethod.Get
                respond(payload, headers = jsonHeaders)
            }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V3/messages/$V3_MESSAGE_ID/status")

                response.status shouldBe OK
                response.bodyAsText() shouldBe payload
                response.body<GetStatusResponse>().statusList?.single()?.sent shouldBe true
            }
        }

        "POST /messages forwards request body and returns 202 from EDI response" {
            val payload = """{
                "businessDocument": "PHhtbC8+",
                "senderHerId": 42,
                "receiverHerIds": [
                    1337
                ],
                "contentType": "application/xml",
                "contentTransferEncoding": "base64",
                "messageTypeIdentificator": "DIALOG_HELSEFAGLIG",
                "applicationName": "Test EPJ",
                "applicationVersion": "1.0",
                "transportMetadataOverrides": {
                    "cpaId": "test-cpa",
                    "conversationId": null,
                    "service": null,
                    "serviceType": null,
                    "action": null,
                    "senderRole": null,
                    "receiverRole": null,
                    "middlewareName": null,
                    "middlewareVersion": null,
                    "compressPayload": null
                }
            }"""
            val result = """{"id":"$V3_MESSAGE_ID"}"""
            val location = "https://nhn.example/messages/$V3_MESSAGE_ID"

            val ediClientV3 = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/messages"
                request.method shouldBe HttpMethod.Post
                JsonUtil.parseToJsonElement((request.body as TextContent).text) shouldBe JsonUtil.parseToJsonElement(
                    payload
                )
                respond(
                    result,
                    Accepted,
                    headersOf(ContentTypeHeader to listOf("application/json"), Location to listOf(location))
                )
            }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)

                val response = client.post("$ROOT_V3/messages") {
                    contentType(Json)
                    setBody(payload)
                }

                response.status shouldBe Accepted
                response.bodyAsText() shouldBe result
                response.headers[Location] shouldBe location
            }
        }

        "POST /messages/{id}/apprec forwards request body and returns 202 from EDI response" {
            val payload = """{
                "appRecSenderHerId": 42,
                "appRecStatus": "Rejected",
                "appRecErrorList": [
                    {
                        "errorCode": "E10",
                        "details": null,
                        "description": null,
                        "oid": null
                    }
                ],
                "applicationName": "Test EPJ",
                "applicationVersion": "1.0",
                "transportMetadataOverrides": {
                    "transportSenderHerId": 42,
                    "transportReceiverHerId": null,
                    "cpaId": null,
                    "conversationId": null,
                    "service": null,
                    "serviceType": null,
                    "action": null,
                    "senderRole": null,
                    "receiverRole": null,
                    "middlewareName": null,
                    "middlewareVersion": null,
                    "compressPayload": null
                }
            }"""
            val result = """{"id":"$V3_MESSAGE_ID"}"""
            val location = "https://nhn.example/messages/$V3_MESSAGE_ID"

            val ediClientV3 = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/messages/$V3_MESSAGE_ID/apprec"
                request.method shouldBe HttpMethod.Post
                JsonUtil.parseToJsonElement((request.body as TextContent).text) shouldBe JsonUtil.parseToJsonElement(
                    payload
                )
                respond(
                    result,
                    Accepted,
                    headersOf(ContentTypeHeader to listOf("application/json"), Location to listOf(location))
                )
            }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)

                val response = client.post("$ROOT_V3/messages/$V3_MESSAGE_ID/apprec") {
                    contentType(Json)
                    setBody(payload)
                }

                response.status shouldBe Accepted
                response.bodyAsText() shouldBe result
                response.headers[Location] shouldBe location
            }
        }

        "PUT /messages/{id}/downloaded forwards request body and returns 204 from EDI response" {
            val payload = """{"receiverHerId":42}"""
            val result = ""
            val location = "https://nhn.example/messages/$V3_MESSAGE_ID"

            val ediClientV3 = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/messages/$V3_MESSAGE_ID/downloaded"
                request.method shouldBe HttpMethod.Put
                JsonUtil.parseToJsonElement((request.body as TextContent).text) shouldBe JsonUtil.parseToJsonElement(
                    payload
                )
                respond(
                    result,
                    NoContent,
                    headersOf(ContentTypeHeader to listOf("application/json"), Location to listOf(location))
                )
            }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)

                val response = client.put("$ROOT_V3/messages/$V3_MESSAGE_ID/downloaded") {
                    contentType(Json)
                    setBody(payload)
                }

                response.status shouldBe NoContent
                response.bodyAsText() shouldBe result
                response.headers[Location] shouldBe location
            }
        }

        "PUT /mshconfigurations forwards request body and returns 204 from EDI response" {
            val payload = """{
                "configurations": [
                    {
                        "herId": 42,
                        "receiveNotificationChannel": "Api",
                        "clientLocked": false,
                        "rejectMessageFilters": {
                            "MessageFunction": [
                                "SVAR_RTG"
                            ]
                        }
                    }
                ]
            }"""
            val result = ""
            val location = "https://nhn.example/messages/$V3_MESSAGE_ID"

            val ediClientV3 = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/mshconfigurations"
                request.method shouldBe HttpMethod.Put
                JsonUtil.parseToJsonElement((request.body as TextContent).text) shouldBe JsonUtil.parseToJsonElement(
                    payload
                )
                respond(
                    result,
                    NoContent,
                    headersOf(ContentTypeHeader to listOf("application/json"), Location to listOf(location))
                )
            }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)

                val response = client.put("$ROOT_V3/mshconfigurations") {
                    contentType(Json)
                    setBody(payload)
                }

                response.status shouldBe NoContent
                response.bodyAsText() shouldBe result
                response.headers[Location] shouldBe location
            }
        }

        "DELETE /mshconfigurations with multiple her ids returns 204 from EDI response" {
            val ediClientV3 = fakeEdiClient { request ->
                request.method shouldBe HttpMethod.Delete
                request.url.fullPath shouldBe "/mshconfigurations?HerIds=42&HerIds=1337"
                respond("", NoContent)
            }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)

                val response = client.delete("$ROOT_V3/mshconfigurations?herIds=42&herIds=1337")

                response.status shouldBe NoContent
                response.bodyAsText() shouldBe ""
            }
        }

        "GET /messages/{id} preserves 400 problem details from EDI response" {
            val problem =
                """{
                    "title": "NHN error",
                    "status": 400,
                    "errorCode": 1210,
                    "requestId": "nhn-request-id"
                }"""

            val ediClientV3 = fakeEdiClient { respond(problem, BadRequest, jsonHeaders) }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)

                val response = client.get("$ROOT_V3/messages/$V3_MESSAGE_ID")

                response.status shouldBe BadRequest
                response.bodyAsText() shouldBe problem
            }
        }

        "GET /messages/{id} preserves 403 problem details from EDI response" {
            val problem =
                """{
                    "title": "NHN error",
                    "status": 403,
                    "errorCode": 1210,
                    "requestId": "nhn-request-id"
                }"""

            val ediClientV3 = fakeEdiClient { respond(problem, Forbidden, jsonHeaders) }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)

                val response = client.get("$ROOT_V3/messages/$V3_MESSAGE_ID")

                response.status shouldBe Forbidden
                response.bodyAsText() shouldBe problem
            }
        }

        "GET /messages/{id} preserves 404 problem details from EDI response" {
            val problem =
                """{
                    "title": "NHN error",
                    "status": 404,
                    "errorCode": 1210,
                    "requestId": "nhn-request-id"
                }"""

            val ediClientV3 = fakeEdiClient { respond(problem, NotFound, jsonHeaders) }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)

                val response = client.get("$ROOT_V3/messages/$V3_MESSAGE_ID")

                response.status shouldBe NotFound
                response.bodyAsText() shouldBe problem
            }
        }

        "GET /messages/{id} preserves 423 problem details from EDI response" {
            val problem =
                """{
                    "title": "NHN error",
                    "status": 423,
                    "errorCode": 1210,
                    "requestId": "nhn-request-id"
                }"""

            val ediClientV3 = fakeEdiClient { respond(problem, Locked, jsonHeaders) }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)

                val response = client.get("$ROOT_V3/messages/$V3_MESSAGE_ID")

                response.status shouldBe Locked
                response.bodyAsText() shouldBe problem
            }
        }

        "GET /messages/{id} preserves 500 problem details from EDI response" {
            val problem =
                """{
                    "title": "NHN error",
                    "status": 500,
                    "errorCode": 1210,
                    "requestId": "nhn-request-id"
                }"""

            val ediClientV3 = fakeEdiClient { respond(problem, InternalServerError, jsonHeaders) }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)

                val response = client.get("$ROOT_V3/messages/$V3_MESSAGE_ID")

                response.status shouldBe InternalServerError
                response.bodyAsText() shouldBe problem
            }
        }

        "POST /messages with empty JSON object returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.post("$ROOT_V3/messages") {
                    contentType(Json)
                    setBody("{}")
                }

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
            }
        }

        "POST /messages with malformed JSON returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.post("$ROOT_V3/messages") {
                    contentType(Json)
                    setBody("not json")
                }

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
            }
        }

        "POST /messages with missing required V3 fields returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.post("$ROOT_V3/messages") {
                    contentType(Json)
                    setBody(
                        """{
                            "businessDocument": "PHhtbC8+",
                            "contentType": "application/xml",
                            "contentTransferEncoding": "base64"
                        }"""
                    )
                }

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
            }
        }

        "POST /messages with unsupported content type returns 415" {
            val payload = """{
                "businessDocument": "PHhtbC8+",
                "senderHerId": 42,
                "receiverHerIds": [
                    1337
                ],
                "contentType": "application/xml",
                "contentTransferEncoding": "base64",
                "messageTypeIdentificator": "DIALOG_HELSEFAGLIG",
                "applicationName": "Test EPJ",
                "applicationVersion": "1.0",
                "transportMetadataOverrides": {
                    "cpaId": "test-cpa",
                    "conversationId": null,
                    "service": null,
                    "serviceType": null,
                    "action": null,
                    "senderRole": null,
                    "receiverRole": null,
                    "middlewareName": null,
                    "middlewareVersion": null,
                    "compressPayload": null
                }
            }"""

            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.post("$ROOT_V3/messages") {
                    contentType(ContentType.Text.Plain)
                    setBody(payload)
                }

                response.status shouldBe UnsupportedMediaType
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 415
            }
        }

        "GET /notifications/stream returns sanitized 500 on unexpected exception" {
            val ediClientV3 = fakeEdiClient { throw IllegalStateException("Internal connection detail") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V3/notifications/stream?herIds=42")

                response.status shouldBe InternalServerError
                val problem = response.body<MshApiProblemDetails>()

                problem.title shouldBe "Internal Server Error"
                problem.detail shouldBe null
                problem.stackTrace shouldBe null
            }
        }

        "GET /notifications/stream with negative offset returns 400" {
            val ediClientV3 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)
                client = createJsonEnabledClient()

                val response = client.get("$ROOT_V3/notifications/stream?herIds=42&offset=-1")

                response.status shouldBe BadRequest
                val problem = response.body<MshApiProblemDetails>()

                problem.status shouldBe 400
                problem.detail shouldBe "Offset must be a non-negative 64-bit integer"
                problem.instance shouldBe "$ROOT_V3/notifications/stream"
            }
        }

        "GET /notifications/stream forwards her ids, offset and SSE accept header" {
            val ediClientV3 = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/notifications/stream?HerIds=42&Offset=41"
                request.headers[Accept] shouldBe "text/event-stream"
                respond("", headers = headersOf(ContentTypeHeader, "text/event-stream"))
            }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)

                val response = client.get("$ROOT_V3/notifications/stream?herIds=42&offset=41")

                response.status shouldBe OK
                response.contentType() shouldBe ContentType.Text.EventStream
            }
        }

        "GET /notifications/stream forwards events before the EDI stream finishes" {
            val firstEventRead = CompletableDeferred<Unit>()
            val stream = ByteChannel(autoFlush = true)
            launch {
                stream.writeStringUtf8("event: notification\ndata: {\"offset\":42}\n\n")
                withTimeout(10000.milliseconds) { firstEventRead.await() }
                stream.writeStringUtf8("event: notification\ndata: {\"offset\":43}\n\n")
                stream.close()
            }
            val ediClientV3 = fakeEdiClient {
                respond(stream, headers = headersOf(ContentTypeHeader, "text/event-stream"))
            }

            ediClientV3.use {
                withStreamingServer(ediClientV3) { baseUrl ->
                    withTimeout(10000.milliseconds) {
                        prepareGet("$baseUrl$ROOT_V3/notifications/stream?herIds=42&offset=41").execute { response ->
                            val body = response.bodyAsChannel()
                            body.readUTF8Line() shouldBe "event: notification"
                            body.readUTF8Line() shouldBe "data: {\"offset\":42}"
                            body.readUTF8Line() shouldBe ""
                            firstEventRead.complete(Unit)
                            body.readUTF8Line() shouldBe "event: notification"
                            body.readUTF8Line() shouldBe "data: {\"offset\":43}"
                        }
                    }
                }
            }
        }

        "GET /notifications/stream closes the upstream call when the client disconnects" {
            val upstreamClosed = CompletableDeferred<Unit>()
            val stream = ByteChannel(autoFlush = true)
            stream.writeStringUtf8("event: notification\ndata: {\"offset\":42}\n\n")
            val ediClientV3 = fakeEdiClient {
                respond(stream, headers = headersOf(ContentTypeHeader, "text/event-stream")).also { response ->
                    response.callContext.job.invokeOnCompletion { upstreamClosed.complete(Unit) }
                }
            }

            ediClientV3.use {
                withStreamingServer(ediClientV3) { baseUrl ->
                    withTimeout(10000.milliseconds) {
                        prepareGet("$baseUrl$ROOT_V3/notifications/stream?herIds=42").execute { response ->
                            val body = response.bodyAsChannel()
                            body.readUTF8Line() shouldBe "event: notification"
                            body.readUTF8Line() shouldBe "data: {\"offset\":42}"
                            body.readUTF8Line() shouldBe ""
                            upstreamClosed.isCompleted shouldBe false
                            response.cancel("Client disconnected")
                        }

                        // Keep upstream active so a write detects the disconnected client.
                        try {
                            while (!stream.isClosedForWrite) {
                                stream.writeStringUtf8(": keep-alive\n\n")
                                delay(10.milliseconds)
                            }
                        } catch (cause: Exception) {
                            currentCoroutineContext().ensureActive()
                            // The channel may close between the check and the write.
                            if (!stream.isClosedForWrite) throw cause
                        }
                        upstreamClosed.await()
                        stream.isClosedForRead shouldBe true
                    }
                }
            }
        }

        "GET /notifications/stream closes without a JSON error when upstream fails after an event" {
            val stream = ByteChannel(autoFlush = true)
            stream.writeStringUtf8("event: notification\ndata: {\"offset\":42}\n\n")
            val ediClientV3 = fakeEdiClient {
                respond(stream, headers = headersOf(ContentTypeHeader, "text/event-stream"))
            }

            ediClientV3.use {
                withStreamingServer(ediClientV3) { baseUrl ->
                    withTimeout(10000.milliseconds) {
                        prepareGet("$baseUrl$ROOT_V3/notifications/stream?herIds=42").execute { response ->
                            response.status shouldBe OK
                            response.contentType() shouldBe ContentType.Text.EventStream
                            val body = response.bodyAsChannel()
                            body.readUTF8Line() shouldBe "event: notification"
                            body.readUTF8Line() shouldBe "data: {\"offset\":42}"
                            body.readUTF8Line() shouldBe ""

                            stream.cancel(IOException("Upstream stream failed"))

                            val remaining = StringBuilder()
                            try {
                                val buffer = ByteArray(1024)
                                while (true) {
                                    val count = body.readAvailable(buffer)
                                    if (count == -1) break
                                    remaining.append(buffer.decodeToString(0, count))
                                }
                            } catch (_: IOException) {
                                // A disconnected stream can surface as EOF or a transport error.
                            }
                            remaining.toString() shouldBe ""
                        }
                    }
                }
            }
        }

        "GET /notifications/stream without offset preserves EDI error as JSON" {
            val problem = """{"status":423,"title":"Locked"}"""

            val ediClientV3 = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/notifications/stream?HerIds=42"
                respond(problem, Locked, jsonHeaders)
            }

            testApplication {
                installExternalRoutes(ediClientV3 = ediClientV3)

                val response = client.get("$ROOT_V3/notifications/stream?herIds=42")

                response.status shouldBe Locked
                response.bodyAsText() shouldBe problem
                response.contentType()?.withoutParameters() shouldBe Json
            }
        }
    }
)
