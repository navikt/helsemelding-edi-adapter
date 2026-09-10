package no.nav.helsemelding.ediadapter.server.plugin

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.Accepted
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.Locked
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.http.HttpStatusCode.Companion.UnsupportedMediaType
import no.nav.helsemelding.ediadapter.model.common.GetBusinessDocumentResponse
import no.nav.helsemelding.ediadapter.model.v3.AppRecStatus
import no.nav.helsemelding.ediadapter.model.v3.ApprecInfo
import no.nav.helsemelding.ediadapter.model.v3.DeliveryState
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
import no.nav.helsemelding.ediadapter.model.v3.StatusInfo

object MessagesApiV3 {

    /* =============================================================
     * GET /notifications
     * ============================================================= */

    const val GET_NOTIFICATIONS = "/notifications"

    val getNotificationsDocs: RouteConfig.() -> Unit = {
        summary = "Get notifications"
        description = "Returns notifications for the supplied HER IDs after the specified offset."
        tags = listOf("V3")

        request {
            queryParameter<List<Int>>("herIds") {
                description = "One to 1500 unique HER IDs. Repeat the parameter for multiple IDs."
                required = true

                example("Multiple HER IDs") {
                    value = listOf(8142520, 8142521)
                }
            }

            queryParameter<Int>("offset") {
                description = "Last successfully processed offset (>= 0). Offsets may have gaps."
                required = true

                example("Last processed offset") {
                    value = 45459
                }
            }

            queryParameter<Int>("notificationsToFetch") {
                description = "Number of notifications to fetch (1–1000, default: 100)."
                required = false

                example("Default page size") {
                    value = 100
                }
            }
        }

        response {
            OK to {
                description = "Notifications retrieved successfully"

                body<GetNotificationsResponse> {
                    example("Notifications") {
                        value = GetNotificationsResponse(
                            notifications = listOf(
                                Notification(
                                    relatedMessageId = "733be787-0ad0-475a-98b7-00512caa9ccb",
                                    type = NotificationType.NEW_MESSAGE,
                                    notificationReceiverHerId = 8142520,
                                    notificationTriggeredByHerId = 8142519,
                                    description = "A new message is available for download.",
                                    createdAt = "2026-05-08T08:32:15.31+00:00",
                                    offset = 45460
                                )
                            )
                        )
                    }
                }
            }

            BadRequest to {
                description = "Invalid request parameters or body"

                body<MshApiProblemDetails> {
                    example("Invalid request") {
                        value = MshApiProblemDetails(
                            title = "Bad Request",
                            status = 400,
                            detail = "Invalid request parameters or body",
                            instance = "/api/v3/notifications",
                            errorCode = 400,
                            requestId = "example-request-id"
                        )
                    }
                }
            }

            Unauthorized to {
                description = "Authentication required or credentials invalid"
                body<MshApiProblemDetails>()
            }

            Forbidden to {
                description = "Access to the requested operation is denied"
                body<MshApiProblemDetails>()
            }

            NotFound to {
                description = "Requested resource not found"
                body<MshApiProblemDetails>()
            }

            UnsupportedMediaType to {
                description = "Unsupported request content type"
                body<MshApiProblemDetails>()
            }

            Locked to {
                description = "The HER ID is locked to another client"
                body<MshApiProblemDetails>()
            }

            InternalServerError to {
                description = "Unexpected server error"
                body<MshApiProblemDetails>()
            }
        }
    }

    /* =============================================================
     * GET /notifications/stream
     * ============================================================= */

    const val STREAM_NOTIFICATIONS = "/notifications/stream"

    val streamNotificationsDocs: RouteConfig.() -> Unit = {
        summary = "Stream notifications using SSE"
        description = "Forwards events as they arrive. Reconnect using the last successfully processed offset."
        tags = listOf("V3")

        request {
            queryParameter<List<Int>>("herIds") {
                description = "One to 1500 unique HER IDs. Repeat the parameter for multiple IDs."
                required = true

                example("Multiple HER IDs") {
                    value = listOf(8142520, 8142521)
                }
            }

            queryParameter<Int>("offset") {
                description = "Last successfully processed offset (>= 0). Offsets may have gaps. Starts at the end of the stream if omitted."
                required = false

                example("Last processed offset") {
                    value = 45459
                }
            }
        }

        response {
            OK to {
                description = "Notification stream established; errors after streaming starts close the stream"

                body<String> {
                    mediaTypes(ContentType.Text.EventStream)
                    description = "SSE events; notification events contain a V3 Notification in the data field."

                    example("Notification stream") {
                        value = """
                            event: connected
                            data:

                            event: notification
                            data: {"relatedMessageId":"733be787-0ad0-475a-98b7-00512caa9ccb","type":"NewMessage","notificationReceiverHerId":8142520,"offset":45460}

                        """.trimIndent() + "\n\n"
                    }
                }
            }

            BadRequest to {
                description = "Invalid request parameters or body"

                body<MshApiProblemDetails> {
                    example("Invalid request") {
                        value = MshApiProblemDetails(
                            title = "Bad Request",
                            status = 400,
                            detail = "Invalid request parameters or body",
                            instance = "/api/v3/notifications/stream",
                            errorCode = 400,
                            requestId = "example-request-id"
                        )
                    }
                }
            }

            Unauthorized to {
                description = "Authentication required or credentials invalid"
                body<MshApiProblemDetails>()
            }

            Forbidden to {
                description = "Access to the requested operation is denied"
                body<MshApiProblemDetails>()
            }

            NotFound to {
                description = "Requested resource not found"
                body<MshApiProblemDetails>()
            }

            UnsupportedMediaType to {
                description = "Unsupported request content type"
                body<MshApiProblemDetails>()
            }

            Locked to {
                description = "The HER ID is locked to another client"
                body<MshApiProblemDetails>()
            }

            InternalServerError to {
                description = "Unexpected server error"
                body<MshApiProblemDetails>()
            }
        }
    }

    /* =============================================================
     * POST /messages
     * ============================================================= */

    const val POST_MESSAGE = "/messages"

    val postMessageDocs: RouteConfig.() -> Unit = {
        summary = "Send a message"
        description = "Submits a Base64-encoded XML document with sender, recipients and application metadata. Maximum request size is 35 MB."
        tags = listOf("V3")

        request {
            body<PostMessageRequest> {
                required = true

                example("Send message") {
                    value = PostMessageRequest(
                        businessDocument = "PHhtbD48RG9jdW1lbnQ+Li4uPC9Eb2N1bWVudD4=",
                        senderHerId = 8142519,
                        receiverHerIds = listOf(8142520),
                        contentType = "application/xml",
                        contentTransferEncoding = "base64",
                        messageTypeIdentificator = "DIALOG_HELSEFAGLIG",
                        applicationName = "EPJ Front",
                        applicationVersion = "18.0.8"
                    )
                }
            }
        }

        response {
            Accepted to {
                description = "Message accepted for sending"

                body<PostMessageResponse> {
                    example("Message reference") {
                        value = PostMessageResponse(id = "733be787-0ad0-475a-98b7-00512caa9ccb")
                    }
                }
            }

            BadRequest to {
                description = "Invalid request parameters or body"

                body<MshApiProblemDetails> {
                    example("Invalid request") {
                        value = MshApiProblemDetails(
                            title = "Bad Request",
                            status = 400,
                            detail = "Invalid request parameters or body",
                            instance = "/api/v3/messages",
                            errorCode = 400,
                            requestId = "example-request-id"
                        )
                    }
                }
            }

            Unauthorized to {
                description = "Authentication required or credentials invalid"
                body<MshApiProblemDetails>()
            }

            Forbidden to {
                description = "Access to the requested operation is denied"
                body<MshApiProblemDetails>()
            }

            NotFound to {
                description = "Requested resource not found"
                body<MshApiProblemDetails>()
            }

            UnsupportedMediaType to {
                description = "Unsupported request content type"
                body<MshApiProblemDetails>()
            }

            Locked to {
                description = "The HER ID is locked to another client"
                body<MshApiProblemDetails>()
            }

            InternalServerError to {
                description = "Unexpected server error"
                body<MshApiProblemDetails>()
            }
        }
    }

    /* =============================================================
     * GET /messages/{messageId}
     * ============================================================= */

    const val GET_MESSAGE = "/messages/{messageId}"

    val getMessageDocs: RouteConfig.() -> Unit = {
        summary = "Get message metadata"
        description = "Returns addressing and business document metadata for a message."
        tags = listOf("V3")

        request {
            pathParameter<String>("messageId") {
                description = "Message ID from a notification or send response."
                required = true

                example("Message ID") {
                    value = "733be787-0ad0-475a-98b7-00512caa9ccb"
                }
            }
        }

        response {
            OK to {
                description = "Message found"

                body<GetMessageResponse> {
                    example("Message metadata") {
                        value = GetMessageResponse(
                            id = "733be787-0ad0-475a-98b7-00512caa9ccb",
                            senderHerId = 8142519,
                            receiverHerIds = listOf(8142520),
                            businessDocumentId = "cc169595-bbf0-11dd-9ca9-117f241b4a68",
                            businessDocumentGenDate = "2026-05-08T08:32:15",
                            businessDocumentMsgType = "DIALOG_HELSEFAGLIG",
                            contentType = "application/xml"
                        )
                    }
                }
            }

            BadRequest to {
                description = "Invalid request parameters or body"

                body<MshApiProblemDetails> {
                    example("Invalid request") {
                        value = MshApiProblemDetails(
                            title = "Bad Request",
                            status = 400,
                            detail = "Invalid request parameters or body",
                            instance = "/api/v3/messages/{messageId}",
                            errorCode = 400,
                            requestId = "example-request-id"
                        )
                    }
                }
            }

            Unauthorized to {
                description = "Authentication required or credentials invalid"
                body<MshApiProblemDetails>()
            }

            Forbidden to {
                description = "Access to the requested operation is denied"
                body<MshApiProblemDetails>()
            }

            NotFound to {
                description = "Requested resource not found"
                body<MshApiProblemDetails>()
            }

            UnsupportedMediaType to {
                description = "Unsupported request content type"
                body<MshApiProblemDetails>()
            }

            Locked to {
                description = "The HER ID is locked to another client"
                body<MshApiProblemDetails>()
            }

            InternalServerError to {
                description = "Unexpected server error"
                body<MshApiProblemDetails>()
            }
        }
    }

    /* =============================================================
     * GET /messages/{messageId}/document
     * ============================================================= */

    const val GET_DOCUMENT = "/messages/{messageId}/document"

    val getDocumentDocs: RouteConfig.() -> Unit = {
        summary = "Get business document"
        description = "Returns the encoded business document associated with a message."
        tags = listOf("V3")

        request {
            pathParameter<String>("messageId") {
                description = "Message ID from a notification or send response."
                required = true

                example("Message ID") {
                    value = "733be787-0ad0-475a-98b7-00512caa9ccb"
                }
            }
        }

        response {
            OK to {
                description = "Business document retrieved successfully"

                body<GetBusinessDocumentResponse> {
                    example("Business document") {
                        value = GetBusinessDocumentResponse(
                            businessDocument = "PHhtbD48RG9jdW1lbnQ+Li4uPC9Eb2N1bWVudD4=",
                            contentType = "application/xml",
                            contentTransferEncoding = "base64"
                        )
                    }
                }
            }

            BadRequest to {
                description = "Invalid request parameters or body"

                body<MshApiProblemDetails> {
                    example("Invalid request") {
                        value = MshApiProblemDetails(
                            title = "Bad Request",
                            status = 400,
                            detail = "Invalid request parameters or body",
                            instance = "/api/v3/messages/{messageId}/document",
                            errorCode = 400,
                            requestId = "example-request-id"
                        )
                    }
                }
            }

            Unauthorized to {
                description = "Authentication required or credentials invalid"
                body<MshApiProblemDetails>()
            }

            Forbidden to {
                description = "Access to the requested operation is denied"
                body<MshApiProblemDetails>()
            }

            NotFound to {
                description = "Requested resource not found"
                body<MshApiProblemDetails>()
            }

            UnsupportedMediaType to {
                description = "Unsupported request content type"
                body<MshApiProblemDetails>()
            }

            Locked to {
                description = "The HER ID is locked to another client"
                body<MshApiProblemDetails>()
            }

            InternalServerError to {
                description = "Unexpected server error"
                body<MshApiProblemDetails>()
            }
        }
    }

    /* =============================================================
     * GET /messages/{messageId}/status
     * ============================================================= */

    const val GET_STATUS = "/messages/{messageId}/status"

    val getStatusDocs: RouteConfig.() -> Unit = {
        summary = "Get delivery and application receipt status"
        description = "Returns transport and application receipt status for each message recipient."
        tags = listOf("V3")

        request {
            pathParameter<String>("messageId") {
                description = "Message ID from a notification or send response."
                required = true

                example("Message ID") {
                    value = "733be787-0ad0-475a-98b7-00512caa9ccb"
                }
            }
        }

        response {
            OK to {
                description = "Message status retrieved successfully"

                body<GetStatusResponse> {
                    example("Message status") {
                        value = GetStatusResponse(
                            statusList = listOf(
                                StatusInfo(
                                    receiverHerId = 8142520,
                                    transportDeliveryState = DeliveryState.ACKNOWLEDGED,
                                    sent = true,
                                    apprecInfo = ApprecInfo(appRecStatus = AppRecStatus.OK)
                                )
                            )
                        )
                    }
                }
            }

            BadRequest to {
                description = "Invalid request parameters or body"

                body<MshApiProblemDetails> {
                    example("Invalid request") {
                        value = MshApiProblemDetails(
                            title = "Bad Request",
                            status = 400,
                            detail = "Invalid request parameters or body",
                            instance = "/api/v3/messages/{messageId}/status",
                            errorCode = 400,
                            requestId = "example-request-id"
                        )
                    }
                }
            }

            Unauthorized to {
                description = "Authentication required or credentials invalid"
                body<MshApiProblemDetails>()
            }

            Forbidden to {
                description = "Access to the requested operation is denied"
                body<MshApiProblemDetails>()
            }

            NotFound to {
                description = "Requested resource not found"
                body<MshApiProblemDetails>()
            }

            UnsupportedMediaType to {
                description = "Unsupported request content type"
                body<MshApiProblemDetails>()
            }

            Locked to {
                description = "The HER ID is locked to another client"
                body<MshApiProblemDetails>()
            }

            InternalServerError to {
                description = "Unexpected server error"
                body<MshApiProblemDetails>()
            }
        }
    }

    /* =============================================================
     * POST /messages/{messageId}/apprec
     * ============================================================= */

    const val POST_APPREC = "/messages/{messageId}/apprec"

    val postApprecDocs: RouteConfig.() -> Unit = {
        summary = "Send an application receipt"
        description = "Generates and sends an application receipt for a received message."
        tags = listOf("V3")

        request {
            pathParameter<String>("messageId") {
                description = "Message ID from a notification or send response."
                required = true

                example("Message ID") {
                    value = "733be787-0ad0-475a-98b7-00512caa9ccb"
                }
            }

            body<PostAppRecRequest> {
                required = true

                example("Accept message") {
                    value = PostAppRecRequest(
                        appRecSenderHerId = 8142520,
                        appRecStatus = AppRecStatus.OK,
                        applicationName = "EPJ Front",
                        applicationVersion = "18.0.8"
                    )
                }
            }
        }

        response {
            Accepted to {
                description = "Application receipt accepted for sending"

                body<PostApprecResponse> {
                    example("Application receipt reference") {
                        value = PostApprecResponse(id = "68e60a2b-5990-408c-b99b-089d8657d6ed")
                    }
                }
            }

            BadRequest to {
                description = "Invalid request parameters or body"

                body<MshApiProblemDetails> {
                    example("Invalid request") {
                        value = MshApiProblemDetails(
                            title = "Bad Request",
                            status = 400,
                            detail = "Invalid request parameters or body",
                            instance = "/api/v3/messages/{messageId}/apprec",
                            errorCode = 400,
                            requestId = "example-request-id"
                        )
                    }
                }
            }

            Unauthorized to {
                description = "Authentication required or credentials invalid"
                body<MshApiProblemDetails>()
            }

            Forbidden to {
                description = "Access to the requested operation is denied"
                body<MshApiProblemDetails>()
            }

            NotFound to {
                description = "Requested resource not found"
                body<MshApiProblemDetails>()
            }

            UnsupportedMediaType to {
                description = "Unsupported request content type"
                body<MshApiProblemDetails>()
            }

            Locked to {
                description = "The HER ID is locked to another client"
                body<MshApiProblemDetails>()
            }

            InternalServerError to {
                description = "Unexpected server error"
                body<MshApiProblemDetails>()
            }
        }
    }

    /* =============================================================
     * PUT /messages/{messageId}/downloaded
     * ============================================================= */

    const val MARK_DOWNLOADED = "/messages/{messageId}/downloaded"

    val markDownloadedDocs: RouteConfig.() -> Unit = {
        summary = "Mark message as downloaded"
        description = "Marks the document as downloaded for the specified recipient."
        tags = listOf("V3")

        request {
            pathParameter<String>("messageId") {
                description = "Message ID from a notification or send response."
                required = true

                example("Message ID") {
                    value = "733be787-0ad0-475a-98b7-00512caa9ccb"
                }
            }

            body<MarkAsDownloadedRequest> {
                required = true

                example("Recipient") {
                    value = MarkAsDownloadedRequest(receiverHerId = 8142520)
                }
            }
        }

        response {
            NoContent to {
                description = "Message marked as downloaded successfully"
            }

            BadRequest to {
                description = "Invalid request parameters or body"

                body<MshApiProblemDetails> {
                    example("Invalid request") {
                        value = MshApiProblemDetails(
                            title = "Bad Request",
                            status = 400,
                            detail = "Invalid request parameters or body",
                            instance = "/api/v3/messages/{messageId}/downloaded",
                            errorCode = 400,
                            requestId = "example-request-id"
                        )
                    }
                }
            }

            Unauthorized to {
                description = "Authentication required or credentials invalid"
                body<MshApiProblemDetails>()
            }

            Forbidden to {
                description = "Access to the requested operation is denied"
                body<MshApiProblemDetails>()
            }

            NotFound to {
                description = "Requested resource not found"
                body<MshApiProblemDetails>()
            }

            UnsupportedMediaType to {
                description = "Unsupported request content type"
                body<MshApiProblemDetails>()
            }

            Locked to {
                description = "The HER ID is locked to another client"
                body<MshApiProblemDetails>()
            }

            InternalServerError to {
                description = "Unexpected server error"
                body<MshApiProblemDetails>()
            }
        }
    }

    /* =============================================================
     * PUT /mshconfigurations
     * ============================================================= */

    const val SET_MSH_CONFIGURATIONS = "/mshconfigurations"

    val setMshConfigurationsDocs: RouteConfig.() -> Unit = {
        summary = "Create or update MSH configurations"
        description = "A configuration is required for each HER ID. External consumers use the Api notification channel."
        tags = listOf("V3")

        request {
            body<SetMshConfigurationsRequest> {
                required = true

                example("API notification configuration") {
                    value = SetMshConfigurationsRequest(
                        configurations = listOf(
                            MshConfiguration(
                                herId = 8142520,
                                receiveNotificationChannel = ReceiveNotificationChannel.API,
                                clientLocked = true
                            )
                        )
                    )
                }
            }
        }

        response {
            NoContent to {
                description = "Configurations applied successfully"
            }

            BadRequest to {
                description = "Invalid request parameters or body"

                body<MshApiProblemDetails> {
                    example("Invalid request") {
                        value = MshApiProblemDetails(
                            title = "Bad Request",
                            status = 400,
                            detail = "Invalid request parameters or body",
                            instance = "/api/v3/mshconfigurations",
                            errorCode = 400,
                            requestId = "example-request-id"
                        )
                    }
                }
            }

            Unauthorized to {
                description = "Authentication required or credentials invalid"
                body<MshApiProblemDetails>()
            }

            Forbidden to {
                description = "Access to the requested operation is denied"
                body<MshApiProblemDetails>()
            }

            NotFound to {
                description = "Requested resource not found"
                body<MshApiProblemDetails>()
            }

            UnsupportedMediaType to {
                description = "Unsupported request content type"
                body<MshApiProblemDetails>()
            }

            Locked to {
                description = "The HER ID is locked to another client"
                body<MshApiProblemDetails>()
            }

            InternalServerError to {
                description = "Unexpected server error"
                body<MshApiProblemDetails>()
            }
        }
    }

    /* =============================================================
     * DELETE /mshconfigurations
     * ============================================================= */

    const val DELETE_MSH_CONFIGURATIONS = "/mshconfigurations"

    val deleteMshConfigurationsDocs: RouteConfig.() -> Unit = {
        summary = "Delete MSH configurations"
        description = "Deletes the configurations for the supplied HER IDs."
        tags = listOf("V3")

        request {
            queryParameter<List<Int>>("herIds") {
                description = "HER IDs whose configurations should be deleted. Repeat the parameter for multiple IDs."
                required = true

                example("Multiple HER IDs") {
                    value = listOf(8142520, 8142521)
                }
            }
        }

        response {
            default {
                description = "NHN response forwarded unchanged. The upstream specification does not define a success status."
            }

            BadRequest to {
                description = "Invalid request parameters or body"

                body<MshApiProblemDetails> {
                    example("Invalid request") {
                        value = MshApiProblemDetails(
                            title = "Bad Request",
                            status = 400,
                            detail = "Invalid request parameters or body",
                            instance = "/api/v3/mshconfigurations",
                            errorCode = 400,
                            requestId = "example-request-id"
                        )
                    }
                }
            }

            Unauthorized to {
                description = "Authentication required or credentials invalid"
                body<MshApiProblemDetails>()
            }

            Forbidden to {
                description = "Access to the requested operation is denied"
                body<MshApiProblemDetails>()
            }

            NotFound to {
                description = "Requested resource not found"
                body<MshApiProblemDetails>()
            }

            UnsupportedMediaType to {
                description = "Unsupported request content type"
                body<MshApiProblemDetails>()
            }

            Locked to {
                description = "The HER ID is locked to another client"
                body<MshApiProblemDetails>()
            }

            InternalServerError to {
                description = "Unexpected server error"
                body<MshApiProblemDetails>()
            }
        }
    }

    /* =============================================================
     * GET /ping
     * ============================================================= */

    const val PING = "/ping"

    val pingDocs: RouteConfig.() -> Unit = {
        summary = "Test the connection to NHN"
        description = "Checks connectivity to the NHN message handler."
        tags = listOf("V3")

        response {
            OK to {
                description = "Connection check completed successfully"

                body<PingResponse> {
                    example("Connection check") {
                        value = PingResponse(
                            response = "Pong",
                            timestampUtc = "2026-05-08T08:32:15Z"
                        )
                    }
                }
            }

            BadRequest to {
                description = "Invalid request parameters or body"

                body<MshApiProblemDetails> {
                    example("Invalid request") {
                        value = MshApiProblemDetails(
                            title = "Bad Request",
                            status = 400,
                            detail = "Invalid request parameters or body",
                            instance = "/api/v3/ping",
                            errorCode = 400,
                            requestId = "example-request-id"
                        )
                    }
                }
            }

            Unauthorized to {
                description = "Authentication required or credentials invalid"
                body<MshApiProblemDetails>()
            }

            Forbidden to {
                description = "Access to the requested operation is denied"
                body<MshApiProblemDetails>()
            }

            NotFound to {
                description = "Requested resource not found"
                body<MshApiProblemDetails>()
            }

            UnsupportedMediaType to {
                description = "Unsupported request content type"
                body<MshApiProblemDetails>()
            }

            Locked to {
                description = "The HER ID is locked to another client"
                body<MshApiProblemDetails>()
            }

            InternalServerError to {
                description = "Unexpected server error"
                body<MshApiProblemDetails>()
            }
        }
    }
}
