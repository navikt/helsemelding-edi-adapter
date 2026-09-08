package no.nav.helsemelding.ediadapter.server.plugin

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import no.nav.helsemelding.ediadapter.model.common.GetBusinessDocumentResponse
import no.nav.helsemelding.ediadapter.model.v3.GetMessageResponse
import no.nav.helsemelding.ediadapter.model.v3.GetNotificationsResponse
import no.nav.helsemelding.ediadapter.model.v3.GetStatusResponse
import no.nav.helsemelding.ediadapter.model.v3.MarkAsDownloadedRequest
import no.nav.helsemelding.ediadapter.model.v3.MshApiProblemDetails
import no.nav.helsemelding.ediadapter.model.v3.PingResponse
import no.nav.helsemelding.ediadapter.model.v3.PostAppRecRequest
import no.nav.helsemelding.ediadapter.model.v3.PostApprecResponse
import no.nav.helsemelding.ediadapter.model.v3.PostMessageRequest
import no.nav.helsemelding.ediadapter.model.v3.PostMessageResponse
import no.nav.helsemelding.ediadapter.model.v3.SetMshConfigurationsRequest

object MessagesApiV3 {
    val getNotificationsDocs: RouteConfig.() -> Unit = {
        v3Documentation("Get notifications")
        notificationParameters()
        response {
            HttpStatusCode.OK to { body<GetNotificationsResponse>() }
        }
    }

    val streamNotificationsDocs: RouteConfig.() -> Unit = {
        v3Documentation("Stream notifications using SSE")
        description = "Forwards NHN SSE events as they arrive. Reconnect using the last successfully processed offset."
        notificationParameters(stream = true)
        response {
            HttpStatusCode.OK to {
                body<String> {
                    mediaTypes(ContentType.Text.EventStream)
                    description = "SSE events with a V3 Notification JSON object in the data field."
                }
            }
        }
    }

    val postMessageDocs: RouteConfig.() -> Unit = {
        v3Documentation("Send a message")
        description = "Send a base64-encoded business document with sender, receivers and application metadata. Maximum request size at NHN is 35 MB."
        request { body<PostMessageRequest> { required = true } }
        response {
            HttpStatusCode.Accepted to { body<PostMessageResponse>() }
        }
    }

    val getMessageDocs: RouteConfig.() -> Unit = {
        v3Documentation("Get message metadata")
        messageIdParameter()
        response {
            HttpStatusCode.OK to { body<GetMessageResponse>() }
        }
    }

    val getDocumentDocs: RouteConfig.() -> Unit = {
        v3Documentation("Get business document")
        messageIdParameter()
        response {
            HttpStatusCode.OK to { body<GetBusinessDocumentResponse>() }
        }
    }

    val getStatusDocs: RouteConfig.() -> Unit = {
        v3Documentation("Get delivery and application receipt status")
        messageIdParameter()
        response {
            HttpStatusCode.OK to { body<GetStatusResponse>() }
        }
    }

    val postApprecDocs: RouteConfig.() -> Unit = {
        v3Documentation("Send an application receipt")
        messageIdParameter()
        request { body<PostAppRecRequest> { required = true } }
        response {
            HttpStatusCode.Accepted to {
                body<PostApprecResponse>()
                header<String>("Location") { description = "Location supplied by NHN, when present." }
            }
        }
    }

    val markDownloadedDocs: RouteConfig.() -> Unit = {
        v3Documentation("Mark message as downloaded")
        messageIdParameter()
        request { body<MarkAsDownloadedRequest> { required = true } }
        response {
            HttpStatusCode.NoContent to { description = "Message marked as downloaded." }
        }
    }

    val setMshConfigurationsDocs: RouteConfig.() -> Unit = {
        v3Documentation("Create or update MSH configurations")
        description = "A configuration is required for each HER ID using NHN V3. Use receiveNotificationChannel Api for external consumers."
        request { body<SetMshConfigurationsRequest> { required = true } }
        response {
            HttpStatusCode.NoContent to { description = "Configurations applied." }
        }
    }

    val deleteMshConfigurationsDocs: RouteConfig.() -> Unit = {
        v3Documentation("Delete MSH configurations")
        herIdsParameter()
        response {
            default {
                description = "NHN response forwarded unchanged. The upstream specification does not define a success status."
            }
        }
    }

    val pingDocs: RouteConfig.() -> Unit = {
        v3Documentation("Test the connection to NHN")
        response {
            HttpStatusCode.OK to { body<PingResponse>() }
        }
    }

    private fun RouteConfig.v3Documentation(routeSummary: String) {
        summary = routeSummary
        tags = listOf("NHN V3")
        response {
            listOf(
                HttpStatusCode.BadRequest,
                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden,
                HttpStatusCode.NotFound,
                HttpStatusCode.UnsupportedMediaType,
                HttpStatusCode.Locked,
                HttpStatusCode.InternalServerError
            ).forEach { status ->
                status to {
                    description = status.description
                    body<MshApiProblemDetails>()
                }
            }
        }
    }

    private fun RouteConfig.messageIdParameter() {
        request {
            pathParameter<String>("messageId") {
                required = true
                description = "Message ID from the notification relatedMessageId or a send response."
            }
        }
    }

    private fun RouteConfig.herIdsParameter(notifications: Boolean = false) {
        request {
            queryParameter<List<Int>>("herIds") {
                required = true
                description = if (notifications) {
                    "One to 1500 unique HER IDs. Repeat the parameter for multiple IDs."
                } else {
                    "HER IDs whose configurations should be deleted. Repeat the parameter for multiple IDs."
                }
            }
        }
    }

    private fun RouteConfig.notificationParameters(stream: Boolean = false) {
        herIdsParameter(notifications = true)
        request {
            queryParameter<Long>("offset") {
                required = !stream
                description = "Last successfully processed offset (>= 0). Offsets may have gaps. SSE starts at the end if omitted."
            }
            if (!stream) {
                queryParameter<Int>("notificationsToFetch") {
                    description = "Number of notifications to fetch: 1–1000; NHN default is 100."
                }
            }
        }
    }
}
