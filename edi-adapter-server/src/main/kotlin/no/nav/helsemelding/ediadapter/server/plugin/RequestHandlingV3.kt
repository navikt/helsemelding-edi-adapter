package no.nav.helsemelding.ediadapter.server.plugin

import arrow.core.raise.Raise
import arrow.core.raise.recover
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.UnsupportedMediaType
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import no.nav.helsemelding.ediadapter.model.v3.MshApiProblemDetails
import no.nav.helsemelding.ediadapter.server.MessageError
import no.nav.helsemelding.ediadapter.server.toContent
import kotlin.time.Clock

private val log = KotlinLogging.logger { }

internal suspend fun RoutingContext.handleV3Request(
    body: suspend Raise<MessageError>.() -> HttpResponse,
    transform: suspend (httpResponse: HttpResponse) -> String = { it.bodyAsText() }
) {
    recover(
        {
            val response = body()
            call.respondV3(response, transform)
        },
        { e: MessageError -> call.respondV3Error(e.toContent()) }
    ) { t: Throwable -> call.handleV3Exception(t) }
}

internal suspend fun RoutingContext.handleV3Stream(
    body: suspend Raise<MessageError>.() -> HttpStatement
) {
    recover(
        { body().execute { response -> call.respondV3Stream(response) } },
        { e: MessageError -> call.respondV3Error(e.toContent()) }
    ) { t: Throwable -> call.handleV3Exception(t) }
}

private suspend fun ApplicationCall.respondV3(
    response: HttpResponse,
    transform: suspend (HttpResponse) -> String = { it.bodyAsText() }
) {
    when (response.status) {
        HttpStatusCode.NoContent -> respond(response.status)
        else -> respondText(
            text = transform(response),
            contentType = response.contentType() ?: Json,
            status = response.status
        )
    }
}

private suspend fun ApplicationCall.respondV3Stream(response: HttpResponse) =
    when (response.status) {
        HttpStatusCode.OK -> {
            this.response.headers.append(HttpHeaders.CacheControl, "no-cache")
            this.response.headers.append("X-Accel-Buffering", "no")
            respondBytesWriter(contentType = ContentType.Text.EventStream) {
                val source = response.bodyAsChannel()
                // Copy in chunks of up to 8 KiB (not an SSE requirement).
                // Reads do not wait for the buffer to fill; each chunk is flushed immediately.
                val buffer = ByteArray(8192)
                while (true) {
                    val count = source.readAvailable(buffer)
                    if (count == -1) break
                    writeFully(buffer, 0, count)
                    flush()
                }
            }
        }

        else -> respondV3(response)
    }

private suspend fun ApplicationCall.respondV3Error(message: TextContent) =
    respondV3Problem(message.status ?: BadRequest, message.text)

private suspend fun ApplicationCall.handleV3Exception(t: Throwable) {
    // Once the response is committed, a new error response cannot be sent.
    if (response.isCommitted) throw t
    when (t) {
        is BadRequestException ->
            respondV3Problem(BadRequest, detail = t.message)
        is UnsupportedMediaTypeException,
        is CannotTransformContentToTypeException ->
            respondV3Problem(UnsupportedMediaType, detail = t.message)
        else -> {
            log.error(t) { "Unexpected error processing request" }
            respondV3Problem(InternalServerError, detail = null)
        }
    }
}

private suspend fun ApplicationCall.respondV3Problem(status: HttpStatusCode, detail: String?) {
    respond(
        status,
        MshApiProblemDetails(
            title = status.description,
            status = status.value,
            detail = detail,
            instance = request.path(),
            errorCode = status.value,
            requestId = callId ?: "unknown",
            timestamp = Clock.System.now().toString()
        )
    )
}
