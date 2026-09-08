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
import no.nav.helsemelding.ediadapter.server.herIds
import no.nav.helsemelding.ediadapter.server.messageId
import no.nav.helsemelding.ediadapter.server.notificationParameters

internal fun Route.v3Routes(ediClient: HttpClient) {
    get("/notifications", MessagesApiV3.getNotificationsDocs) {
        handleV3Request(
            {
                val params = notificationParameters(call)
                ediClient.get("notifications") { url { parameters.appendAll(params) } }
            }
        )
    }

    get("/notifications/stream", MessagesApiV3.streamNotificationsDocs) {
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

    post("/messages", MessagesApiV3.postMessageDocs) {
        handleV3Request(
            {
                val message = call.receive<PostMessageRequest>()
                ediClient.post("messages") {
                    contentType(ContentType.Application.Json)
                    setBody(message)
                }
            }
        )
    }

    get("/messages/{messageId}", MessagesApiV3.getMessageDocs) {
        handleV3Request(
            {
                val messageId = messageId(call)
                ediClient.get("messages/$messageId")
            }
        )
    }

    get("/messages/{messageId}/document", MessagesApiV3.getDocumentDocs) {
        handleV3Request(
            {
                val messageId = messageId(call)
                ediClient.get("messages/$messageId/business-document")
            }
        )
    }

    get("/messages/{messageId}/status", MessagesApiV3.getStatusDocs) {
        handleV3Request(
            {
                val messageId = messageId(call)
                ediClient.get("messages/$messageId/status")
            }
        )
    }

    post("/messages/{messageId}/apprec", MessagesApiV3.postApprecDocs) {
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

    put("/messages/{messageId}/downloaded", MessagesApiV3.markDownloadedDocs) {
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

    put("/mshconfigurations", MessagesApiV3.setMshConfigurationsDocs) {
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

    delete("/mshconfigurations", MessagesApiV3.deleteMshConfigurationsDocs) {
        handleV3Request(
            {
                val herIds = herIds(call)
                ediClient.delete("mshconfigurations") { url { parameters.appendAll("HerIds", herIds) } }
            }
        )
    }

    get("/ping", MessagesApiV3.pingDocs) {
        handleV3Request(
            { ediClient.get("ping") }
        )
    }
}
