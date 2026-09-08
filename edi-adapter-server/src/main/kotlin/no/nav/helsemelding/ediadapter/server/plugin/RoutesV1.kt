package no.nav.helsemelding.ediadapter.server.plugin

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Location
import io.ktor.http.contentType
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import no.nav.helsemelding.ediadapter.model.v1.Metadata
import no.nav.helsemelding.ediadapter.model.v1.PostAppRecRequest
import no.nav.helsemelding.ediadapter.model.v1.PostMessageRequest
import no.nav.helsemelding.ediadapter.server.apprecSenderHerId
import no.nav.helsemelding.ediadapter.server.herId
import no.nav.helsemelding.ediadapter.server.messageId
import no.nav.helsemelding.ediadapter.server.messageQueryParams
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV1.GET_APPREC
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV1.GET_DOCUMENT
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV1.GET_MESSAGE
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV1.GET_MESSAGES
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV1.GET_STATUS
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV1.MARK_READ
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV1.POST_APPREC
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV1.POST_MESSAGE
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV1.getApprecDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV1.getDocumentDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV1.getMessageDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV1.getMessagesDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV1.getStatusDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV1.markReadDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV1.postApprecDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV1.postMessageDocs
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json as JsonUtil

internal fun Route.v1Routes(ediClient: HttpClient) {
    get(GET_MESSAGES, getMessagesDocs) {
        handleRequest(
            {
                val params = messageQueryParams(call)
                ediClient.get("Messages") { url { parameters.appendAll(params) } }
            }
        )
    }

    get(GET_MESSAGE, getMessageDocs) {
        handleRequest(
            {
                val messageId = messageId(call)
                ediClient.get("Messages/$messageId")
            }
        )
    }

    get(GET_DOCUMENT, getDocumentDocs) {
        handleRequest(
            {
                val messageId = messageId(call)
                ediClient.get("Messages/$messageId/business-document")
            }
        )
    }

    get(GET_STATUS, getStatusDocs) {
        handleRequest(
            {
                val messageId = messageId(call)
                ediClient.get("Messages/$messageId/status")
            }
        )
    }

    get(GET_APPREC, getApprecDocs) {
        handleRequest(
            {
                val messageId = messageId(call)
                ediClient.get("Messages/$messageId/apprec")
            }
        )
    }

    post(POST_MESSAGE, postMessageDocs) {
        val message = call.receive<PostMessageRequest>()
        handleRequest(
            {
                ediClient.post("Messages") {
                    contentType(Json)
                    setBody(message)
                }
            },
            { it.toMetadata() }
        )
    }

    post(POST_APPREC, postApprecDocs) {
        val appRec = call.receive<PostAppRecRequest>()
        handleRequest(
            {
                val messageId = messageId(call)
                val senderHerId = apprecSenderHerId(call)

                ediClient.post("Messages/$messageId/apprec/$senderHerId") {
                    contentType(Json)
                    setBody(appRec)
                }
            },
            { it.toMetadata() }
        )
    }

    put(MARK_READ, markReadDocs) {
        handleRequest(
            {
                val messageId = messageId(call)
                val herId = herId(call)
                ediClient.put("Messages/$messageId/read/$herId")
            }
        )
    }
}

private suspend fun HttpResponse.toMetadata(): String {
    val body = bodyAsText()
    val location = headers[Location] ?: return body

    val id = JsonUtil.decodeFromString<Uuid>(body)

    val metadata = Metadata(
        id = id,
        location = location
    )

    return JsonUtil.encodeToString(metadata)
}
