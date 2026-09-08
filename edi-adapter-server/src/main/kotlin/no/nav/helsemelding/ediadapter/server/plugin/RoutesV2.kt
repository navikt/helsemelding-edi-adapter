package no.nav.helsemelding.ediadapter.server.plugin

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.contentType
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import no.nav.helsemelding.ediadapter.model.v2.PostMshConfigurationRequest
import no.nav.helsemelding.ediadapter.server.noticeQueryParams
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV2.GET_NOTICES
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV2.POST_MSH_CONFIGURATION
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV2.getNoticesDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApiV2.postMshConfigurationDocs

internal fun Route.v2Routes(ediClient: HttpClient) {
    get(GET_NOTICES, getNoticesDocs) {
        handleRequest(
            {
                val params = noticeQueryParams(call)
                ediClient.get("Messages/notices") { url { parameters.appendAll(params) } }
            }
        )
    }

    post(POST_MSH_CONFIGURATION, postMshConfigurationDocs) {
        val message = call.receive<PostMshConfigurationRequest>()
        handleRequest(
            {
                ediClient.post("MshConfiguration") {
                    contentType(Json)
                    setBody(message)
                }
            }
        )
    }
}
