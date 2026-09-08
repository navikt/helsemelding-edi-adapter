package no.nav.helsemelding.ediadapter.server.plugin

import arrow.core.raise.Raise
import arrow.core.raise.recover
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.content.TextContent
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import no.nav.helsemelding.ediadapter.model.common.ErrorMessage
import no.nav.helsemelding.ediadapter.server.MessageError
import no.nav.helsemelding.ediadapter.server.toContent

private val log = KotlinLogging.logger { }

internal suspend fun RoutingContext.handleRequest(
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
