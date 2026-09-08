package no.nav.helsemelding.ediadapter.server.plugin

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.body
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.HttpStatusCode.Companion.UnsupportedMediaType
import io.ktor.http.contentType
import io.ktor.http.fullPath
import io.ktor.server.testing.testApplication
import no.nav.helsemelding.ediadapter.model.common.ErrorMessage

private const val ROOT_V2 = "/api/v2"

class RoutesV2Spec : StringSpec(
    {
        val getToken = routeTokenProvider()

        "GET /messages/notices with single receiver her id returns EDI response" {
            val ediClientV2 = fakeEdiClient {
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
                installExternalRoutes(ediClientV2 = ediClientV2)

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
            val ediClientV2 = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages/notices?ReceiverHerIds=1&ReceiverHerIds=2"
                respond(
                    """[
                    {"id":"100", "noticeType": "NewMessage", "receiverHerId": "1"}, 
                    {"id":"200", "noticeType": "RefusedMessage", "receiverHerId": "2"}
                ]"""
                )
            }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClientV2)

                val response = client.get("$ROOT_V2/messages/notices?receiverHerIds=1&receiverHerIds=2")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[
                    {"id":"100", "noticeType": "NewMessage", "receiverHerId": "1"}, 
                    {"id":"200", "noticeType": "RefusedMessage", "receiverHerId": "2"}
                ]"""
            }
        }

        "GET /messages/notices with receiver her id and message notices to fetch returns EDI response" {
            val ediClientV2 = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages/notices?ReceiverHerIds=1&MessagesToFetch=1"
                respond("""[{"id":"100", "noticeType": "NewMessage", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClientV2)

                val response = client.get("$ROOT_V2/messages/notices?receiverHerIds=1&messagesToFetch=1")

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "noticeType": "NewMessage", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages/notices with receiver her id and message notices to fetch (0) returns error" {
            val ediClientV2 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClientV2)
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
            val ediClientV2 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClientV2)
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
            val ediClientV2 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClientV2)

                val response = client.get("$ROOT_V2/messages/notices?receiverHerIds=")
                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Receiver her ids"
            }
        }

        "GET /messages/notices without receiver her id returns 400" {
            val ediClientV2 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClientV2)

                val response = client.get("$ROOT_V2/messages/notices")

                response.status shouldBe BadRequest
                response.bodyAsText() shouldContain "Receiver her ids"
            }
        }

        "GET /messages/notices returns EDI response with authentication" {
            val ediClientV2 = fakeEdiClient {
                it.url.fullPath shouldBe "/Messages/notices?ReceiverHerIds=1"
                respond("""[{"id":"100", "receiverHerId": "1"}]""")
            }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClientV2, useAuthentication = true)

                val response = client.getWithAuth("$ROOT_V2/messages/notices?receiverHerIds=1", getToken)

                response.status shouldBe OK
                response.bodyAsText() shouldBe """[{"id":"100", "receiverHerId": "1"}]"""
            }
        }

        "GET /messages/notices returns Unauthorised if access token is missing" {
            val ediClientV2 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClientV2, useAuthentication = true)

                val response = client.get("$ROOT_V2/messages/notices?id=1")

                response.status shouldBe Unauthorized
            }
        }

        "GET /messages/notices returns Unauthorised if access token is invalid" {
            val ediClientV2 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClientV2, useAuthentication = true)

                val response = client.getWithAuth("$ROOT_V2/messages/notices?id=1", getToken, INVALID_AUDIENCE)

                response.status shouldBe Unauthorized
            }
        }

        "POST /mshConfiguration returns 204 no content from EDI response" {
            val ediClientV2 = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/MshConfiguration"
                respond(
                    content = "",
                    status = NoContent
                )
            }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClientV2)
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
            val ediClientV2 = fakeEdiClient { throw RuntimeException("boom") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClientV2)
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
            val ediClientV2 = fakeEdiClient { request ->
                request.url.fullPath shouldBe "/MshConfiguration"
                respond(
                    content = "",
                    status = NoContent
                )
            }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClientV2, useAuthentication = true)
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
            val ediClientV2 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClientV2, useAuthentication = true)
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
            val ediClientV2 = fakeEdiClient { error("Should not be called") }

            testApplication {
                installExternalRoutes(ediClientV2 = ediClientV2, useAuthentication = true)
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

                val response = client.postWithAuth("$ROOT_V2/mshConfiguration", getToken, INVALID_AUDIENCE) {
                    contentType(Json)
                    setBody(body)
                }

                response.status shouldBe Unauthorized
            }
        }
    }
)
