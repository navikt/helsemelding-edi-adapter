package no.nav.helsemelding.ediadapter.server.plugin

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import no.nav.helsemelding.ediadapter.model.v3.MarkAsDownloadedRequest
import no.nav.helsemelding.ediadapter.model.v3.PostAppRecRequest
import no.nav.helsemelding.ediadapter.model.v3.PostMessageRequest
import no.nav.helsemelding.ediadapter.model.v3.SetMshConfigurationsRequest
import no.nav.helsemelding.ediadapter.server.config
import no.nav.helsemelding.ediadapter.server.herIds
import no.nav.helsemelding.ediadapter.server.messageId
import no.nav.helsemelding.ediadapter.server.notificationParameters
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.DELETE_MSH_CONFIGURATIONS
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.GET_DOCUMENT
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.GET_MESSAGE
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.GET_NOTIFICATIONS
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.GET_STATUS
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.MARK_DOWNLOADED
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.PING
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.POST_APPREC
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.POST_MESSAGE
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.SET_MSH_CONFIGURATIONS
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.STREAM_NOTIFICATIONS
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.deleteMshConfigurationsDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.getDocumentDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.getMessageDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.getNotificationsDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.getStatusDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.markDownloadedDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.pingDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.postApprecDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.postMessageDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.setMshConfigurationsDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV3.streamNotificationsDocs
import no.nav.helsemelding.ediadapter.server.model.PostMessageRequest as InternalPostMessageRequest

internal fun Route.v3Routes(ediClient: HttpClient) {
    get(GET_NOTIFICATIONS, getNotificationsDocs) {
        handleV3Request(
            {
                val params = notificationParameters(call)
                ediClient.get("notifications") { url { parameters.appendAll(params) } }
            }
        )
    }

    get(STREAM_NOTIFICATIONS, streamNotificationsDocs) {
        handleV3Stream(
            {
                val params = notificationParameters(call, stream = true)
                ediClient.prepareGet("notifications/stream") {
                    accept(ContentType.Text.EventStream)
                    url { parameters.appendAll(params) }
                }
            }
        )
    }

    post(POST_MESSAGE, postMessageDocs) {
        handleV3Request(
            {
                val message = call.receive<PostMessageRequest>()
                ediClient.post("messages") {
                    contentType(ContentType.Application.Json)
                    setBody(InternalPostMessageRequest.from(message, config().nhn))
                }
            }
        )
    }

    get(GET_MESSAGE, getMessageDocs) {
        handleV3Request(
            {
                val messageId = messageId(call)
                ediClient.get("messages/$messageId")
            }
        )
    }

    get(GET_DOCUMENT, getDocumentDocs) {
        handleV3Request(
            {
                val messageId = messageId(call)
                ediClient.get("messages/$messageId/business-document")
            }
        )
    }

    get(GET_STATUS, getStatusDocs) {
        handleV3Request(
            {
                val messageId = messageId(call)
                ediClient.get("messages/$messageId/status")
            }
        )
    }

    post(POST_APPREC, postApprecDocs) {
        handleV3Request(
            {
                val apprec = call.receive<PostAppRecRequest>()
                val messageId = messageId(call)
                ediClient.post("messages/$messageId/apprec") {
                    contentType(ContentType.Application.Json)
                    setBody(apprec)
                }
            }
        )
    }

    put(MARK_DOWNLOADED, markDownloadedDocs) {
        handleV3Request(
            {
                val downloaded = call.receive<MarkAsDownloadedRequest>()
                val messageId = messageId(call)
                ediClient.put("messages/$messageId/downloaded") {
                    contentType(ContentType.Application.Json)
                    setBody(downloaded)
                }
            }
        )
    }

    put(SET_MSH_CONFIGURATIONS, setMshConfigurationsDocs) {
        handleV3Request(
            {
                val configurations = call.receive<SetMshConfigurationsRequest>()
                ediClient.put("mshconfigurations") {
                    contentType(ContentType.Application.Json)
                    setBody(configurations)
                }
            }
        )
    }

    delete(DELETE_MSH_CONFIGURATIONS, deleteMshConfigurationsDocs) {
        handleV3Request(
            {
                val herIds = herIds(call)
                ediClient.delete("mshconfigurations") { url { parameters.appendAll("HerIds", herIds) } }
            }
        )
    }

    get(PING, pingDocs) {
        handleV3Request(
            { ediClient.get("ping") }
        )
    }
}
