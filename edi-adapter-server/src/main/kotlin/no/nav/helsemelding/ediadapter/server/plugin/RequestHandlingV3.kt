package no.nav.helsemelding.ediadapter.server.plugin

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.recover
import arrow.fx.coroutines.onCancel
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
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.ensureActive
import no.nav.helsemelding.ediadapter.model.v3.MshApiProblemDetails
import no.nav.helsemelding.ediadapter.server.MessageError
import no.nav.helsemelding.ediadapter.server.toContent
import kotlin.time.Clock
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

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
    val relay = SseRelay(call)
    onCancel({
        Either.catch {
            recover({ relay.forward(body()) }) { error: MessageError -> relay.reject(error) }
        }
            .onLeft { cause ->
                // Netty can surface the socket error while unwinding a cancelled request.
                call.coroutineContext.ensureActive()
                val message = relay.endReason("failed", cause)
                call.handleV3Exception(
                    cause,
                    logCommitted = { log.warn { message } },
                    logUnexpected = { log.error { message } }
                )
            }
    }) { log.info { relay.endReason("cancelled") } }
}

private class SseRelay(private val call: ApplicationCall) {
    private val streamId = call.request.headers[HttpHeaders.XCorrelationId]
        ?.let { Either.catch { Uuid.parse(it) }.getOrNull() } ?: Uuid.random()
    private val stream = "streamId=$streamId path=${call.request.path()}"
    private val started = TimeSource.Monotonic.markNow()
    private var operation = "opening the NHN connection"

    fun endReason(outcome: String, cause: Throwable? = null): String = buildString {
        append("SSE $outcome while $operation $stream")
        cause?.let { append(" cause=${it.sseCauseTypes()}") }
        append(" duration=${started.elapsedNow()}")
    }

    suspend fun reject(error: MessageError) {
        val content = error.toContent()
        log.warn { "SSE request rejected $stream reason=HTTP ${(content.status ?: BadRequest).value}" }
        call.respondV3Error(content)
    }

    suspend fun forward(statement: HttpStatement) {
        log.info { "Opening SSE connection to NHN $stream" }
        statement.execute { upstream ->
            when (upstream.status) {
                HttpStatusCode.OK -> forwardEvents(upstream)
                else -> forwardStatus(upstream)
            }
        }
    }

    private suspend fun forwardStatus(upstream: HttpResponse) {
        val message = "SSE connection to NHN ended $stream reason=HTTP ${upstream.status.value}"
        when (upstream.status) {
            HttpStatusCode.NoContent -> log.info { message }
            else -> log.warn { message }
        }
        operation = "forwarding the NHN response to the client"
        call.respondV3(upstream)
    }

    @OptIn(InternalAPI::class)
    private suspend fun forwardEvents(upstream: HttpResponse) {
        log.info { "SSE connected to NHN $stream" }
        operation = "writing to the client"
        call.response.headers.append(HttpHeaders.CacheControl, "no-cache")
        call.response.headers.append("X-Accel-Buffering", "no")
        call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
            operation = "reading from NHN"
            val source = upstream.bodyAsChannel()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var count = source.readAvailable(buffer)
            while (count >= 0) {
                operation = "writing to the client"
                writeFully(buffer, 0, count)
                flush()
                operation = "reading from NHN"
                count = source.readAvailable(buffer)
            }
            // Ktor's bodyAsChannel copy can end normally when the network channel already failed.
            val closeCause = source.closedCause ?: upstream.rawContent.closedCause
            closeCause?.let { throw it }
            operation = "finishing the response to the client"
        }
        log.info { "SSE closed $stream reason=NHN EOF duration=${started.elapsedNow()}" }
    }
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

private fun Throwable.sseCauseTypes(): String =
    generateSequence(this) { it.cause }
        .take(8)
        .map { it.javaClass.simpleName }
        .distinct()
        .joinToString(" -> ")

private suspend fun ApplicationCall.respondV3Error(message: TextContent) =
    respondV3Problem(message.status ?: BadRequest, message.text)

private suspend fun ApplicationCall.handleV3Exception(
    t: Throwable,
    logCommitted: () -> Unit = {},
    logUnexpected: () -> Unit = { log.error(t) { "Unexpected error processing request" } }
) {
    when {
        response.isCommitted -> {
            logCommitted()
            throw t
        }

        t is BadRequestException ->
            respondV3Problem(BadRequest, detail = t.message)

        t is UnsupportedMediaTypeException || t is CannotTransformContentToTypeException ->
            respondV3Problem(UnsupportedMediaType, detail = t.message)

        else -> {
            logUnexpected()
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
