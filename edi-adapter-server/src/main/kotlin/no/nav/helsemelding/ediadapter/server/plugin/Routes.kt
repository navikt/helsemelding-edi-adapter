package no.nav.helsemelding.ediadapter.server.plugin

import arrow.core.raise.Raise
import arrow.core.raise.recover
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.content.TextContent
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.Location
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.helsemelding.ediadapter.model.ErrorMessage
import no.nav.helsemelding.ediadapter.model.Metadata
import no.nav.helsemelding.ediadapter.model.PostAppRecRequest
import no.nav.helsemelding.ediadapter.model.PostMessageRequest
import no.nav.helsemelding.ediadapter.model.PostMshConfigurationRequest
import no.nav.helsemelding.ediadapter.server.MessageError
import no.nav.helsemelding.ediadapter.server.ValidationError
import no.nav.helsemelding.ediadapter.server.apprecSenderHerId
import no.nav.helsemelding.ediadapter.server.businessDocumentId
import no.nav.helsemelding.ediadapter.server.config
import no.nav.helsemelding.ediadapter.server.herId
import no.nav.helsemelding.ediadapter.server.includeMetadata
import no.nav.helsemelding.ediadapter.server.messageId
import no.nav.helsemelding.ediadapter.server.messagesToFetch
import no.nav.helsemelding.ediadapter.server.orderBy
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.GET_APPREC
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.GET_DOCUMENT
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.GET_MESSAGE
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.GET_MESSAGES
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.GET_NOTICES
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.GET_STATUS
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.MARK_READ
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.POST_APPREC
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.POST_MESSAGE
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.POST_MSH_CONFIGURATION
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.getApprecDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.getDocumentDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.getMessageDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.getMessagesDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.getNoticesDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.getStatusDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.markReadDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.postApprecDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.postMessageDocs
import no.nav.helsemelding.ediadapter.server.plugin.MessagesApi.postMshConfigurationDocs
import no.nav.helsemelding.ediadapter.server.receiverHerIds
import no.nav.helsemelding.ediadapter.server.toContent
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json as JsonUtil

private val log = KotlinLogging.logger { }

private const val RECEIVER_HER_IDS = "ReceiverHerIds"
private const val BUSINESS_DOCUMENT_ID = "BusinessDocumentId"
private const val INCLUDE_METADATA = "IncludeMetadata"
private const val MESSAGES_TO_FETCH = "MessagesToFetch"
private const val ORDER_BY = "OrderBy"

fun Application.configureRoutes(
    ediClientV1: HttpClient,
    ediClientV2: HttpClient,
    registry: PrometheusMeterRegistry
) {
    routing {
        swaggerRoutes()
        internalRoutes(registry)

        authenticate(config().azureAuth.issuer.value) {
            externalRoutes(ediClientV1, ediClientV2)
        }
    }
}

fun Route.swaggerRoutes() {
    route("api.json") {
        openApi()
    }
    route("swagger") {
        swaggerUI("/api.json") {
        }
    }
}

fun Route.internalRoutes(registry: PrometheusMeterRegistry) {
    get("/prometheus") {
        call.respond(registry.scrape())
    }
    route("/internal") {
        get("/health/liveness") {
            call.respondText("I'm alive! :)")
        }
        get("/health/readiness") {
            call.respondText("I'm ready! :)")
        }
    }
}

fun Route.externalRoutes(ediClientV1: HttpClient, ediClientV2: HttpClient) {
    route("/api/v1") {
        get(GET_MESSAGES, getMessagesDocs) {
            handleRequest(
                {
                    val params = messageQueryParams(call)
                    ediClientV1.get("Messages") { url { parameters.appendAll(params) } }
                }
            )
        }

        get(GET_MESSAGE, getMessageDocs) {
            handleRequest(
                {
                    val messageId = messageId(call)
                    ediClientV1.get("Messages/$messageId")
                }
            )
        }

        get(GET_DOCUMENT, getDocumentDocs) {
            handleRequest(
                {
                    val messageId = messageId(call)
                    ediClientV1.get("Messages/$messageId/business-document")
                }
            )
        }

        get(GET_STATUS, getStatusDocs) {
            handleRequest(
                {
                    val messageId = messageId(call)
                    ediClientV1.get("Messages/$messageId/status")
                }
            )
        }

        get(GET_APPREC, getApprecDocs) {
            handleRequest(
                {
                    val messageId = messageId(call)
                    ediClientV1.get("Messages/$messageId/apprec")
                }
            )
        }

        post(POST_MESSAGE, postMessageDocs) {
            val message = call.receive<PostMessageRequest>()
            handleRequest(
                {
                    ediClientV1.post("Messages") {
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

                    ediClientV1.post("Messages/$messageId/apprec/$senderHerId") {
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
                    ediClientV1.put("Messages/$messageId/read/$herId")
                }
            )
        }
    }

    route("/api/v2") {
        get(GET_NOTICES, getNoticesDocs) {
            handleRequest(
                {
                    val params = noticeQueryParams(call)
                    ediClientV2.get("Messages/notices") { url { parameters.appendAll(params) } }
                }
            )
        }

        post(POST_MSH_CONFIGURATION, postMshConfigurationDocs) {
            val message = call.receive<PostMshConfigurationRequest>()
            handleRequest(
                {
                    ediClientV2.post("MshConfiguration") {
                        contentType(Json)
                        setBody(message)
                    }
                }
            )
        }
    }
}

private suspend fun RoutingContext.handleRequest(
    body: suspend Raise<MessageError>.() -> HttpResponse,
    transform: suspend (httpResponse: HttpResponse) -> String = { it.bodyAsText() }
) {
    recover(
        {
            val response = body()
            call.respondText(
                text = transform(response),
                contentType = Json,
                status = response.status
            )
        },
        { e: MessageError -> call.respondError(e.toContent()) }
    ) { t: Throwable -> call.respondInternalError(t) }
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

private fun Raise<ValidationError>.messageQueryParams(
    call: ApplicationCall
): Parameters {
    val receiverHerIds = receiverHerIds(call)
    val businessDocumentId = businessDocumentId(call)
    val includeMetadata = includeMetadata(call)
    val messagesToFetch = messagesToFetch(call)
    val orderBy = orderBy(call)

    return Parameters.build {
        appendAll(RECEIVER_HER_IDS, receiverHerIds)
        appendIfPresent(BUSINESS_DOCUMENT_ID, businessDocumentId)
        appendIfPresent(INCLUDE_METADATA, includeMetadata)
        appendIfPresent(MESSAGES_TO_FETCH, messagesToFetch)
        appendIfPresent(ORDER_BY, orderBy)
    }
}

private fun Raise<ValidationError>.noticeQueryParams(
    call: ApplicationCall
): Parameters {
    val receiverHerIds = receiverHerIds(call)
    val messagesToFetch = messagesToFetch(call)

    return Parameters.build {
        appendAll(RECEIVER_HER_IDS, receiverHerIds)
        appendIfPresent(MESSAGES_TO_FETCH, messagesToFetch)
    }
}

private fun ParametersBuilder.appendIfPresent(name: String, value: Any?) =
    value?.let { append(name, it.toString()) }

private suspend fun ApplicationCall.respondError(message: TextContent) {
    val status = message.status ?: BadRequest
    respond(
        status = status,
        message = ErrorMessage(
            error = message.text,
            errorCode = status.value,
            requestId = callId ?: "unknown"
        )
    )
}

private suspend fun ApplicationCall.respondInternalError(t: Throwable) {
    log.error(t) { "Unexpected error while processing request" }
    respond(
        status = InternalServerError,
        message = ErrorMessage(
            error = InternalServerError.description,
            errorCode = 500,
            requestId = callId ?: "unknown"
        )
    )
}
