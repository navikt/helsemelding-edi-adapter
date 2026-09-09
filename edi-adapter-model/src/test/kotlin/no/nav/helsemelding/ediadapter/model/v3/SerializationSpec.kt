package no.nav.helsemelding.ediadapter.model.v3

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class SerializationSpec : StringSpec(
    {
        "AppRecError serializes and deserializes all fields" {
            assertSerialization(
                AppRecError("E10", "Invalid document", "Validation failed", "8221"),
                """
                {
                  "errorCode": "E10",
                  "details": "Invalid document",
                  "description": "Validation failed",
                  "oid": "8221"
                }
                """
            )
        }

        "ApprecInfo serializes and deserializes all fields" {
            assertSerialization(
                ApprecInfo(AppRecStatus.REJECTED, listOf(AppRecError("E10", "Invalid document"))),
                """
                {
                  "appRecStatus": "Rejected",
                  "appRecErrorList": [
                    {
                      "errorCode": "E10",
                      "details": "Invalid document"
                    }
                  ]
                }
                """
            )
        }

        "MessageTransportMetadataOverrides serializes and deserializes all fields" {
            assertSerialization(
                MessageTransportMetadataOverrides(
                    cpaId = "cpa",
                    conversationId = "conversation",
                    service = "service",
                    serviceType = "type",
                    action = "action",
                    senderRole = "sender",
                    receiverRole = "receiver",
                    middlewareName = "middleware",
                    middlewareVersion = "1.0",
                    compressPayload = false
                ),
                """
                {
                  "cpaId": "cpa",
                  "conversationId": "conversation",
                  "service": "service",
                  "serviceType": "type",
                  "action": "action",
                  "senderRole": "sender",
                  "receiverRole": "receiver",
                  "middlewareName": "middleware",
                  "middlewareVersion": "1.0",
                  "compressPayload": false
                }
                """
            )
        }

        "AppRecTransportMetadataOverrides serializes and deserializes all fields" {
            assertSerialization(
                AppRecTransportMetadataOverrides(
                    transportSenderHerId = 123,
                    transportReceiverHerId = 456,
                    cpaId = "cpa",
                    conversationId = "conversation",
                    service = "service",
                    serviceType = "type",
                    action = "action",
                    senderRole = "sender",
                    receiverRole = "receiver",
                    middlewareName = "middleware",
                    middlewareVersion = "1.0",
                    compressPayload = false
                ),
                """
                {
                  "transportSenderHerId": 123,
                  "transportReceiverHerId": 456,
                  "cpaId": "cpa",
                  "conversationId": "conversation",
                  "service": "service",
                  "serviceType": "type",
                  "action": "action",
                  "senderRole": "sender",
                  "receiverRole": "receiver",
                  "middlewareName": "middleware",
                  "middlewareVersion": "1.0",
                  "compressPayload": false
                }
                """
            )
        }

        "GetMessageResponse serializes and deserializes all fields" {
            assertSerialization(
                GetMessageResponse(
                    id = "message-id",
                    senderHerId = 123,
                    receiverHerIds = listOf(456, 789),
                    businessDocumentId = "document-id",
                    businessDocumentGenDate = "2026-05-08T08:32:15.31",
                    businessDocumentMsgType = "message-type",
                    contentType = "application/xml"
                ),
                """
                {
                  "id": "message-id",
                  "senderHerId": 123,
                  "receiverHerIds": [
                    456,
                    789
                  ],
                  "businessDocumentId": "document-id",
                  "businessDocumentGenDate": "2026-05-08T08:32:15.31",
                  "businessDocumentMsgType": "message-type",
                  "contentType": "application/xml"
                }
                """
            )
        }

        "Notification serializes and deserializes all fields" {
            assertSerialization(
                Notification(
                    relatedMessageId = "message-id",
                    type = NotificationType.NEW_MESSAGE,
                    notificationReceiverHerId = 456,
                    notificationTriggeredByHerId = 123,
                    description = "New message received",
                    createdAt = "2026-05-08T08:32:15.31+00:00",
                    offset = 2147483647
                ),
                """
                {
                  "relatedMessageId": "message-id",
                  "type": "NewMessage",
                  "notificationReceiverHerId": 456,
                  "notificationTriggeredByHerId": 123,
                  "description": "New message received",
                  "createdAt": "2026-05-08T08:32:15.31+00:00",
                  "offset": 2147483647
                }
                """
            )
        }

        "GetNotificationsResponse serializes and deserializes all fields" {
            assertSerialization(
                GetNotificationsResponse(
                    listOf(
                        Notification(type = NotificationType.NEW_MESSAGE, notificationReceiverHerId = 456, offset = 0)
                    )
                ),
                """
                {
                  "notifications": [
                    {
                      "type": "NewMessage",
                      "notificationReceiverHerId": 456,
                      "offset": 0
                    }
                  ]
                }
                """
            )
        }

        "StatusInfo serializes and deserializes all fields" {
            assertSerialization(
                StatusInfo(456, DeliveryState.ACKNOWLEDGED, true, ApprecInfo(AppRecStatus.OK)),
                """
                {
                  "receiverHerId": 456,
                  "transportDeliveryState": "Acknowledged",
                  "sent": true,
                  "apprecInfo": {
                    "appRecStatus": "Ok"
                  }
                }
                """
            )
        }

        "GetStatusResponse serializes and deserializes all fields" {
            assertSerialization(
                GetStatusResponse(listOf(StatusInfo(456, DeliveryState.UNCONFIRMED, false))),
                """
                {
                  "statusList": [
                    {
                      "receiverHerId": 456,
                      "transportDeliveryState": "Unconfirmed",
                      "sent": false
                    }
                  ]
                }
                """
            )
        }

        "MarkAsDownloadedRequest serializes and deserializes all fields" {
            assertSerialization(
                MarkAsDownloadedRequest(456),
                """
                {
                  "receiverHerId": 456
                }
                """
            )
        }

        "MshApiProblemDetails serializes and deserializes all fields" {
            assertSerialization(
                MshApiProblemDetails(
                    type = "about:blank",
                    title = "Bad request",
                    status = 400,
                    detail = "Invalid receiver",
                    instance = "/messages",
                    errorCode = 100,
                    requestId = "request-id",
                    validationErrors = listOf("Receiver is required"),
                    stackTrace = "trace",
                    timestamp = "2026-05-08T08:32:15.31+00:00"
                ),
                """
                {
                  "type": "about:blank",
                  "title": "Bad request",
                  "status": 400,
                  "detail": "Invalid receiver",
                  "instance": "/messages",
                  "errorCode": 100,
                  "requestId": "request-id",
                  "validationErrors": [
                    "Receiver is required"
                  ],
                  "stackTrace": "trace",
                  "timestamp": "2026-05-08T08:32:15.31+00:00"
                }
                """
            )
        }

        "MshConfiguration serializes and deserializes all fields" {
            assertSerialization(
                MshConfiguration(123, ReceiveNotificationChannel.API, false, RejectMessageFilters(listOf("function"))),
                """
                {
                  "herId": 123,
                  "receiveNotificationChannel": "Api",
                  "clientLocked": false,
                  "rejectMessageFilters": {
                    "MessageFunction": [
                      "function"
                    ]
                  }
                }
                """
            )
        }

        "SetMshConfigurationsRequest serializes and deserializes all fields" {
            assertSerialization(
                SetMshConfigurationsRequest(listOf(MshConfiguration(123, ReceiveNotificationChannel.API))),
                """
                {
                  "configurations": [
                    {
                      "herId": 123,
                      "receiveNotificationChannel": "Api"
                    }
                  ]
                }
                """
            )
        }

        "RejectMessageFilters serializes and deserializes all fields" {
            assertSerialization(
                RejectMessageFilters(listOf("function", "another-function")),
                """
                {
                  "MessageFunction": [
                    "function",
                    "another-function"
                  ]
                }
                """
            )
        }

        "PingResponse serializes and deserializes all fields" {
            assertSerialization(
                PingResponse("pong", "2026-05-08T08:32:15.31+00:00"),
                """
                {
                  "response": "pong",
                  "timestampUtc": "2026-05-08T08:32:15.31+00:00"
                }
                """
            )
        }

        "PostAppRecRequest serializes and deserializes all fields" {
            assertSerialization(
                PostAppRecRequest(
                    appRecSenderHerId = 456,
                    appRecStatus = AppRecStatus.REJECTED,
                    appRecErrorList = listOf(AppRecError("E10", "Invalid document")),
                    applicationName = "application",
                    applicationVersion = "1.0",
                    transportMetadataOverrides = AppRecTransportMetadataOverrides(transportSenderHerId = 456)
                ),
                """
                {
                  "appRecSenderHerId": 456,
                  "appRecStatus": "Rejected",
                  "appRecErrorList": [
                    {
                      "errorCode": "E10",
                      "details": "Invalid document"
                    }
                  ],
                  "applicationName": "application",
                  "applicationVersion": "1.0",
                  "transportMetadataOverrides": {
                    "transportSenderHerId": 456
                  }
                }
                """
            )
        }

        "PostMessageRequest serializes and deserializes all fields" {
            assertSerialization(
                PostMessageRequest(
                    businessDocument = "<Document>æøå</Document>",
                    senderHerId = 123,
                    receiverHerIds = listOf(456, 789),
                    contentType = "application/xml",
                    contentTransferEncoding = "8bit",
                    messageTypeIdentificator = "message-type",
                    applicationName = "application",
                    applicationVersion = "1.0",
                    transportMetadataOverrides = MessageTransportMetadataOverrides(compressPayload = true)
                ),
                """
                {
                  "businessDocument": "<Document>æøå</Document>",
                  "senderHerId": 123,
                  "receiverHerIds": [
                    456,
                    789
                  ],
                  "contentType": "application/xml",
                  "contentTransferEncoding": "8bit",
                  "messageTypeIdentificator": "message-type",
                  "applicationName": "application",
                  "applicationVersion": "1.0",
                  "transportMetadataOverrides": {
                    "compressPayload": true
                  }
                }
                """
            )
        }

        "PostMessageResponse serializes and deserializes all fields" {
            assertSerialization(
                PostMessageResponse("message-id"),
                """
                {
                  "id": "message-id"
                }
                """
            )
        }

        "PostApprecResponse serializes and deserializes all fields" {
            assertSerialization(
                PostApprecResponse("message-id"),
                """
                {
                  "id": "message-id"
                }
                """
            )
        }

        "AppRecError preserves nullable and default fields" {
            val expected = AppRecError(null, null)
            assertSerialization(
                expected,
                """
                {
                  "errorCode": null,
                  "details": null
                }
                """
            )
            Json.decodeFromString<AppRecError>(
                """
                {
                  "errorCode": null,
                  "details": null,
                  "description": null,
                  "oid": null
                }
                """
            ) shouldBe expected
        }

        "ApprecInfo preserves nullable and default fields" {
            val expected = ApprecInfo()
            assertSerialization(
                expected,
                """
                {}
                """
            )
            Json.decodeFromString<ApprecInfo>(
                """
                {
                  "appRecStatus": null,
                  "appRecErrorList": null
                }
                """
            ) shouldBe expected
        }

        "AppRecTransportMetadataOverrides preserves nullable and default fields" {
            val expected = AppRecTransportMetadataOverrides()
            assertSerialization(
                expected,
                """
                {}
                """
            )
            Json.decodeFromString<AppRecTransportMetadataOverrides>(
                """
                {
                  "transportSenderHerId": null,
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
                """
            ) shouldBe expected
        }

        "MessageTransportMetadataOverrides preserves nullable and default fields" {
            val expected = MessageTransportMetadataOverrides()
            assertSerialization(
                expected,
                """
                {}
                """
            )
            Json.decodeFromString<MessageTransportMetadataOverrides>(
                """
                {
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
                """
            ) shouldBe expected
        }

        "GetMessageResponse preserves nullable and default fields" {
            val expected = GetMessageResponse("message-id")
            assertSerialization(
                expected,
                """
                {
                  "id": "message-id"
                }
                """
            )
            Json.decodeFromString<GetMessageResponse>(
                """
                {
                  "id": "message-id",
                  "senderHerId": null,
                  "receiverHerIds": null,
                  "businessDocumentId": null,
                  "businessDocumentGenDate": null,
                  "businessDocumentMsgType": null,
                  "contentType": null
                }
                """
            ) shouldBe expected
        }

        "GetStatusResponse preserves nullable and default fields" {
            val expected = GetStatusResponse()
            assertSerialization(
                expected,
                """
                {}
                """
            )
            Json.decodeFromString<GetStatusResponse>(
                """
                {
                  "statusList": null
                }
                """
            ) shouldBe expected
        }

        "MshApiProblemDetails preserves nullable and default fields" {
            val expected = MshApiProblemDetails()
            assertSerialization(
                expected,
                """
                {}
                """
            )
            Json.decodeFromString<MshApiProblemDetails>(
                """
                {
                  "type": null,
                  "title": null,
                  "status": null,
                  "detail": null,
                  "instance": null,
                  "errorCode": null,
                  "requestId": null,
                  "validationErrors": null,
                  "stackTrace": null,
                  "timestamp": null
                }
                """
            ) shouldBe expected
        }

        "MshConfiguration preserves nullable and default fields" {
            val expected = MshConfiguration(123, ReceiveNotificationChannel.API)
            assertSerialization(
                expected,
                """
                {
                  "herId": 123,
                  "receiveNotificationChannel": "Api"
                }
                """
            )
            Json.decodeFromString<MshConfiguration>(
                """
                {
                  "herId": 123,
                  "receiveNotificationChannel": "Api",
                  "clientLocked": null,
                  "rejectMessageFilters": null
                }
                """
            ) shouldBe expected
        }

        "Notification preserves nullable and default fields" {
            val expected = Notification(type = NotificationType.NEW_MESSAGE, notificationReceiverHerId = 456, offset = 0)
            assertSerialization(
                expected,
                """
                {
                  "type": "NewMessage",
                  "notificationReceiverHerId": 456,
                  "offset": 0
                }
                """
            )
            Json.decodeFromString<Notification>(
                """
                {
                  "type": "NewMessage",
                  "notificationReceiverHerId": 456,
                  "offset": 0,
                  "relatedMessageId": null,
                  "notificationTriggeredByHerId": null,
                  "description": null,
                  "createdAt": null
                }
                """
            ) shouldBe expected
        }

        "PingResponse preserves nullable and default fields" {
            val expected = PingResponse("pong")
            assertSerialization(
                expected,
                """
                {
                  "response": "pong"
                }
                """
            )
            Json.decodeFromString<PingResponse>(
                """
                {
                  "response": "pong",
                  "timestampUtc": null
                }
                """
            ) shouldBe expected
        }

        "PostAppRecRequest preserves nullable and default fields" {
            val expected = PostAppRecRequest(456, AppRecStatus.OK, applicationName = "application", applicationVersion = "1.0")
            assertSerialization(
                expected,
                """
                {
                  "appRecSenderHerId": 456,
                  "appRecStatus": "Ok",
                  "applicationName": "application",
                  "applicationVersion": "1.0"
                }
                """
            )
            Json.decodeFromString<PostAppRecRequest>(
                """
                {
                  "appRecSenderHerId": 456,
                  "appRecStatus": "Ok",
                  "applicationName": "application",
                  "applicationVersion": "1.0",
                  "appRecErrorList": null,
                  "transportMetadataOverrides": null
                }
                """
            ) shouldBe expected
        }

        "PostMessageRequest preserves nullable and default fields" {
            val expected = PostMessageRequest("document", 123, listOf(456), "application/xml", "8bit", "type", "application", "1.0")
            assertSerialization(
                expected,
                """
                {
                  "businessDocument": "document",
                  "senderHerId": 123,
                  "receiverHerIds": [
                    456
                  ],
                  "contentType": "application/xml",
                  "contentTransferEncoding": "8bit",
                  "messageTypeIdentificator": "type",
                  "applicationName": "application",
                  "applicationVersion": "1.0"
                }
                """
            )
            Json.decodeFromString<PostMessageRequest>(
                """
                {
                  "businessDocument": "document",
                  "senderHerId": 123,
                  "receiverHerIds": [
                    456
                  ],
                  "contentType": "application/xml",
                  "contentTransferEncoding": "8bit",
                  "messageTypeIdentificator": "type",
                  "applicationName": "application",
                  "applicationVersion": "1.0",
                  "transportMetadataOverrides": null
                }
                """
            ) shouldBe expected
        }

        "PostMessageResponse preserves nullable and default fields" {
            val expected = PostMessageResponse()
            assertSerialization(
                expected,
                """
                {}
                """
            )
            Json.decodeFromString<PostMessageResponse>(
                """
                {
                  "id": null
                }
                """
            ) shouldBe expected
        }

        "PostApprecResponse preserves nullable and default fields" {
            val expected = PostApprecResponse()
            assertSerialization(
                expected,
                """
                {}
                """
            )
            Json.decodeFromString<PostApprecResponse>(
                """
                {
                  "id": null
                }
                """
            ) shouldBe expected
        }

        "RejectMessageFilters preserves nullable and default fields" {
            val expected = RejectMessageFilters()
            assertSerialization(
                expected,
                """
                {}
                """
            )
            Json.decodeFromString<RejectMessageFilters>(
                """
                {
                  "MessageFunction": null
                }
                """
            ) shouldBe expected
        }

        "StatusInfo preserves nullable and default fields" {
            val expected = StatusInfo(456, DeliveryState.UNCONFIRMED, false)
            assertSerialization(
                expected,
                """
                {
                  "receiverHerId": 456,
                  "transportDeliveryState": "Unconfirmed",
                  "sent": false
                }
                """
            )
            Json.decodeFromString<StatusInfo>(
                """
                {
                  "receiverHerId": 456,
                  "transportDeliveryState": "Unconfirmed",
                  "sent": false,
                  "apprecInfo": null
                }
                """
            ) shouldBe expected
        }

        "AppRecStatus uses the API values for every entry" {
            val values = mapOf(
                AppRecStatus.OK to "Ok",
                AppRecStatus.REJECTED to "Rejected",
                AppRecStatus.OK_ERROR_IN_MESSAGE_PART to "OkErrorInMessagePart"
            )
            values.keys shouldBe AppRecStatus.entries.toSet()
            values.forEach { (value, serialName) ->
                assertSerialization(value, "\"$serialName\"")
            }
        }

        "DeliveryState uses the API values for every entry" {
            val values = mapOf(
                DeliveryState.UNCONFIRMED to "Unconfirmed",
                DeliveryState.ACKNOWLEDGED to "Acknowledged",
                DeliveryState.REJECTED to "Rejected",
                DeliveryState.ABANDONED to "Abandoned"
            )
            values.keys shouldBe DeliveryState.entries.toSet()
            values.forEach { (value, serialName) ->
                assertSerialization(value, "\"$serialName\"")
            }
        }

        "NotificationType uses the API values for every entry" {
            val values = mapOf(
                NotificationType.NEW_MESSAGE to "NewMessage",
                NotificationType.REFUSED_MESSAGE to "RefusedMessage",
                NotificationType.MESSAGE_SENT_STATE_UPDATED to "MessageSentStateUpdated",
                NotificationType.MESSAGE_APPREC_INFO_UPDATED to "MessageApprecInfoUpdated",
                NotificationType.MESSAGE_DELIVERY_STATE_UPDATED to "MessageDeliveryStateUpdated"
            )
            values.keys shouldBe NotificationType.entries.toSet()
            values.forEach { (value, serialName) ->
                assertSerialization(value, "\"$serialName\"")
            }
        }

        "ReceiveNotificationChannel uses the API values for every entry" {
            val values = mapOf(
                ReceiveNotificationChannel.API to "Api"
            )
            values.keys shouldBe ReceiveNotificationChannel.entries.toSet()
            values.forEach { (value, serialName) ->
                assertSerialization(value, "\"$serialName\"")
            }
        }
    }
)

private inline fun <reified T> assertSerialization(expected: T, payload: String) {
    Json.decodeFromString<T>(payload) shouldBe expected
    Json.parseToJsonElement(Json.encodeToString(expected)) shouldBe Json.parseToJsonElement(payload)
}
