package no.nav.helsemelding.ediadapter.client

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.getOrElse
import arrow.core.nonFatalOrThrow
import arrow.core.raise.Raise
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.resilience.Schedule
import arrow.resilience.Schedule.Decision.Continue
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.ClientSSESessionWithDeserialization
import io.ktor.client.plugins.sse.SSEClientException
import io.ktor.client.plugins.sse.deserialize
import io.ktor.client.plugins.sse.serverSentEventsSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpMethod.Companion.Delete
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import no.nav.helsemelding.ediadapter.model.common.GetBusinessDocumentResponse
import no.nav.helsemelding.ediadapter.model.v3.GetMessageResponse
import no.nav.helsemelding.ediadapter.model.v3.GetNotificationsResponse
import no.nav.helsemelding.ediadapter.model.v3.GetStatusResponse
import no.nav.helsemelding.ediadapter.model.v3.MarkAsDownloadedRequest
import no.nav.helsemelding.ediadapter.model.v3.MshApiProblemDetails
import no.nav.helsemelding.ediadapter.model.v3.Notification
import no.nav.helsemelding.ediadapter.model.v3.PingResponse
import no.nav.helsemelding.ediadapter.model.v3.PostAppRecRequest
import no.nav.helsemelding.ediadapter.model.v3.PostApprecResponse
import no.nav.helsemelding.ediadapter.model.v3.PostMessageRequest
import no.nav.helsemelding.ediadapter.model.v3.PostMessageResponse
import no.nav.helsemelding.ediadapter.model.v3.SetMshConfigurationsRequest
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/**
 * Typed client for the EDI Adapter's V3 API, including notification streaming through [Flow].
 *
 * Requests return [Either] with an [EdiAdapterError] on failure. Notification streams reconnect
 * automatically on transient failures; ordinary HTTP requests are not retried. Cancellation propagates.
 *
 * Cancel active notification collections before calling [close] to release the client's resources.
 */
interface EdiAdapterClient : AutoCloseable {
    /**
     * Fetches notifications for the requested her ids after a stored offset.
     *
     * @param herIds Between 1 and 1500 unique receiver her ids.
     * @param offset Last processed notification offset, or `0` when no offset has been stored. Must be nonnegative.
     * @param notificationsToFetch Maximum number of notifications to fetch, from 1 to 1000. Null uses the server default.
     * @return [Either.Right] containing the notifications, possibly empty, or [Either.Left] containing an [EdiAdapterError].
     */
    suspend fun getNotifications(
        herIds: List<Int>,
        offset: Int,
        notificationsToFetch: Int? = null
    ): Either<EdiAdapterError, GetNotificationsResponse>

    /**
     * Fetches notifications for a single her id after a stored offset.
     *
     * @param herId Receiver her id whose notifications to fetch.
     * @param offset Last processed notification offset, or `0` when no offset has been stored. Must be nonnegative.
     * @param notificationsToFetch Maximum number of notifications to fetch, from 1 to 1000. Null uses the server default.
     * @return [Either.Right] containing the notifications, possibly empty, or [Either.Left] containing an [EdiAdapterError].
     */
    suspend fun getNotifications(
        herId: Int,
        offset: Int,
        notificationsToFetch: Int? = null
    ): Either<EdiAdapterError, GetNotificationsResponse> =
        getNotifications(listOf(herId), offset, notificationsToFetch)

    /**
     * Opens a cold stream of notifications; each collection owns its connection.
     *
     * Reconnects on EOF, transport failures and HTTP 408, 429 or 5xx, with capped exponential backoff.
     * Other HTTP errors and invalid notifications emit one Left and end the flow. HTTP 204 ends it normally.
     * Empty `connected` events and events other than `notification` are ignored.
     *
     * [offset] is the initial nonnegative offset. Use `0` on first startup or when no processed offset
     * has been stored; otherwise use the latest stored offset.
     * Each collection advances its reconnect offset after emitting a notification. With buffering,
     * emission does not guarantee that processing has completed.
     * Persist processed offsets separately for recovery after cancellation or application restart.
     * Null starts at the current tail until the first notification is emitted; reconnects before that
     * can miss notifications during disconnection. Processing should tolerate redelivery.
     * Cancelling collection closes the connection. Configuration errors, unexpected failures and
     * exceptions from the collector propagate without reconnecting.
     *
     * @param herIds Between 1 and 1500 unique receiver her ids whose notifications to stream.
     * @param offset Initial nonnegative offset. Use the latest stored offset, or `0` on first startup.
     *     Null starts at the current end of the stream.
     * @return A cold [Flow] emitting [Either.Right] notifications or a terminal [Either.Left] with an
     *     [EdiAdapterError]. Retryable failures are handled internally without emitting a Left.
     */
    fun streamNotifications(herIds: List<Int>, offset: Int? = null): Flow<Either<EdiAdapterError, Notification>>

    /**
     * Streams notifications for a single her id. Use `0` on first startup or when no processed offset
     * has been stored; otherwise use the latest stored offset. Omitting [offset] starts at the current tail.
     * Uses the same reconnect, error handling and cancellation behavior as the list overload.
     *
     * @param herId Receiver her id whose notifications to stream.
     * @param offset Initial nonnegative offset, or null to start at the current end of the stream.
     * @return A cold [Flow] emitting [Either.Right] notifications or a terminal [Either.Left] with an
     *     [EdiAdapterError]. Retryable failures are handled internally without emitting a Left.
     */
    fun streamNotifications(
        herId: Int,
        offset: Int? = null
    ): Flow<Either<EdiAdapterError, Notification>> = streamNotifications(listOf(herId), offset)

    /**
     * Submits a business document for delivery to its recipients.
     *
     * Acceptance does not mean that delivery or application processing has completed; use [getMessageStatus]
     * or notifications to follow progress.
     *
     * @param request Encoded business document, sender and receiver her ids, content metadata and application details.
     * @return [Either.Right] containing the accepted submission's [PostMessageResponse], or [Either.Left]
     *     containing an [EdiAdapterError].
     */
    suspend fun postMessage(request: PostMessageRequest): Either<EdiAdapterError, PostMessageResponse>

    /**
     * Retrieves a message's addressing and business document metadata.
     *
     * @param id Identifier of the message to retrieve.
     * @return [Either.Right] containing [GetMessageResponse], or [Either.Left] containing an [EdiAdapterError].
     */
    suspend fun getMessage(id: Uuid): Either<EdiAdapterError, GetMessageResponse>

    /**
     * Retrieves the encoded business document associated with a message.
     *
     * @param id Identifier of the message whose document to retrieve.
     * @return [Either.Right] containing the document, content type and transfer encoding in
     *     [GetBusinessDocumentResponse], or [Either.Left] containing an [EdiAdapterError].
     */
    suspend fun getBusinessDocument(id: Uuid): Either<EdiAdapterError, GetBusinessDocumentResponse>

    /**
     * Retrieves delivery state and application receipt information for each receiver of a message.
     *
     * @param id Identifier of the message whose status to retrieve.
     * @return [Either.Right] containing the receiver statuses in [GetStatusResponse], or [Either.Left]
     *     containing an [EdiAdapterError].
     */
    suspend fun getMessageStatus(id: Uuid): Either<EdiAdapterError, GetStatusResponse>

    /**
     * Submits an application receipt for a received message.
     *
     * @param id Identifier of the original message being acknowledged.
     * @param request Receipt sender her id, processing status, application details and any rejection errors.
     * @return [Either.Right] containing the accepted receipt submission's [PostApprecResponse], or [Either.Left]
     *     containing an [EdiAdapterError].
     */
    suspend fun postApprec(id: Uuid, request: PostAppRecRequest): Either<EdiAdapterError, PostApprecResponse>

    /**
     * Marks a message as downloaded by a specific receiver.
     *
     * @param id Identifier of the downloaded message.
     * @param request Receiver her id for which the message should be marked as downloaded.
     * @return [Either.Right] containing [Unit] on success, or [Either.Left] containing an [EdiAdapterError].
     */
    suspend fun markMessageAsDownloaded(id: Uuid, request: MarkAsDownloadedRequest): Either<EdiAdapterError, Unit>

    /**
     * Creates or updates message handler configurations for the supplied communication parties.
     *
     * @param request Configurations containing her ids, notification channels and optional client locks and rejection filters.
     * @return [Either.Right] containing [Unit] on success, or [Either.Left] containing an [EdiAdapterError].
     */
    suspend fun setMshConfigurations(request: SetMshConfigurationsRequest): Either<EdiAdapterError, Unit>

    /**
     * Deletes message handler configurations for the specified her ids.
     *
     * @param herIds Communication party her ids whose configurations should be deleted.
     * @return [Either.Right] containing [Unit] on success, or [Either.Left] containing an [EdiAdapterError].
     */
    suspend fun deleteMshConfigurations(herIds: List<Int>): Either<EdiAdapterError, Unit>

    /**
     * Checks connectivity to NHN through the adapter.
     *
     * @return [Either.Right] containing the response text and available timestamp in [PingResponse],
     *     or [Either.Left] containing an [EdiAdapterError].
     */
    suspend fun ping(): Either<EdiAdapterError, PingResponse>

    /**
     * Closes the underlying HTTP client and releases its resources.
     *
     * Cancel active notification collections before closing. Do not reuse the client afterwards.
     *
     * @return [Unit] after requesting shutdown of the HTTP client.
     */
    override fun close()
}

class HttpEdiAdapterClient(
    clientProvider: () -> HttpClient,
    ediAdapterUrl: String = config().ediAdapterServer.url.toString()
) : EdiAdapterClient {
    private val baseUrl = "${ediAdapterUrl.trimEnd('/')}/api/v3"
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = clientProvider()
    private val reconnectSchedule = Schedule.exponential<Unit>(1.seconds)
        .delayed { _, duration -> duration.coerceAtMost(30.seconds) }
        .jittered(min = 0.5, max = 1.0)

    override suspend fun getNotifications(
        herIds: List<Int>,
        offset: Int,
        notificationsToFetch: Int?
    ): Either<EdiAdapterError, GetNotificationsResponse> = request(Get, "notifications") {
        herIds.forEach { parameter("herIds", it) }
        parameter("offset", offset)
        parameter("notificationsToFetch", notificationsToFetch)
    }

    override suspend fun postMessage(request: PostMessageRequest): Either<EdiAdapterError, PostMessageResponse> =
        request(Post, "messages") { jsonBody(request) }

    override suspend fun getMessage(id: Uuid): Either<EdiAdapterError, GetMessageResponse> =
        request(Get, "messages/$id")

    override suspend fun getBusinessDocument(id: Uuid): Either<EdiAdapterError, GetBusinessDocumentResponse> =
        request(Get, "messages/$id/document")

    override suspend fun getMessageStatus(id: Uuid): Either<EdiAdapterError, GetStatusResponse> =
        request(Get, "messages/$id/status")

    override suspend fun postApprec(id: Uuid, request: PostAppRecRequest): Either<EdiAdapterError, PostApprecResponse> =
        request(Post, "messages/$id/apprec") { jsonBody(request) }

    override suspend fun markMessageAsDownloaded(
        id: Uuid,
        request: MarkAsDownloadedRequest
    ): Either<EdiAdapterError, Unit> =
        request(Put, "messages/$id/downloaded") { jsonBody(request) }

    override suspend fun setMshConfigurations(request: SetMshConfigurationsRequest): Either<EdiAdapterError, Unit> =
        request(Put, "mshconfigurations") { jsonBody(request) }

    override suspend fun deleteMshConfigurations(herIds: List<Int>): Either<EdiAdapterError, Unit> =
        request(Delete, "mshconfigurations") {
            herIds.forEach { parameter("herIds", it) }
        }

    override suspend fun ping(): Either<EdiAdapterError, PingResponse> = request(Get, "ping")

    override fun streamNotifications(
        herIds: List<Int>,
        offset: Int?
    ): Flow<Either<EdiAdapterError, Notification>> = flow {
        either {
            var resumeOffset = offset
            var reconnect = reconnectSchedule.step
            while (true) {
                val finished = collectNotifications(herIds, resumeOffset) { notification ->
                    emit(Right(notification))
                    resumeOffset = notification.offset
                    reconnect = reconnectSchedule.step
                }
                    .getOrElse { error ->
                        ensure(shouldReconnect(error)) { emit(Left(error)) }
                        false
                    }
                ensure(!finished) { }
                // NHN resumes via the notification offset, rather than SSE's Last-Event-ID.
                val decision = reconnect(Unit)
                ensure(decision is Continue) { }
                log.debug { "Reconnecting notification stream in ${decision.delay}" }
                delay(decision.delay)
                reconnect = decision.step
            }
        }
    }

    override fun close() {
        httpClient.close()
    }

    private suspend fun collectNotifications(
        herIds: List<Int>,
        offset: Int?,
        onNotification: suspend (Notification) -> Unit
    ): Either<EdiAdapterError, Boolean> = either {
        var finished = false
        flow {
            val session = openNotificationSession(herIds, offset)
            finished = session.call.response.status == HttpStatusCode.NoContent
            emitAll(session.notifications())
        }
            .catch { cause -> raise(streamError(cause)) }
            .collect { onNotification(it) }
        finished
    }

    private suspend fun openNotificationSession(
        herIds: List<Int>,
        offset: Int?
    ): ClientSSESessionWithDeserialization {
        log.debug { "Opening notification stream for her ids: $herIds from offset $offset" }
        return httpClient.serverSentEventsSession(
            "$baseUrl/notifications/stream",
            deserialize = { type, data ->
                val serializer = json.serializersModule.serializer(type.kotlinType!!)
                json.decodeFromString(serializer, data)
            }
        ) {
            herIds.forEach { parameter("herIds", it) }
            parameter("offset", offset)
        }
            .also { session ->
                log.debug { "Notification stream response: ${session.call.response.status}" }
            }
    }

    private fun ClientSSESessionWithDeserialization.notifications(): Flow<Notification> = incoming
        .filter { it.event == "notification" }
        .map { event ->
            deserialize<Notification>(event.data)
                ?: throw SerializationException("Missing notification data")
        }
        .onCompletion {
            cancel()
            log.debug { "Notification stream closed" }
        }

    private suspend fun streamError(cause: Throwable): EdiAdapterError {
        val error = cause.nonFatalOrThrow()
        val response = (error as? SSEClientException)?.response
        log.debug { "Notification stream failed: ${error::class.simpleName}, HTTP status: ${response?.status}" }
        val underlying = generateSequence(error) { (it as? SSEClientException)?.cause }
            .last().nonFatalOrThrow()
        return when {
            response != null && !response.status.isSuccess() -> apiError(response)
            underlying is SerializationException -> EdiAdapterError.Decoding(underlying)
            underlying is IOException -> EdiAdapterError.Transport(underlying)
            response != null -> EdiAdapterError.Decoding(error)
            else -> throw underlying
        }
    }

    private fun shouldReconnect(error: EdiAdapterError): Boolean = when (error) {
        is EdiAdapterError.Transport -> true
        is EdiAdapterError.Api -> error.status in 500..599 || error.status == 408 || error.status == 429
        is EdiAdapterError.Decoding -> false
    }

    private inline fun <reified T> HttpRequestBuilder.jsonBody(value: T) {
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(value))
    }

    private suspend inline fun <reified T> request(
        method: HttpMethod,
        path: String,
        crossinline configure: HttpRequestBuilder.() -> Unit = {}
    ): Either<EdiAdapterError, T> = either {
        catch({
            val response = httpClient.request("$baseUrl/$path") {
                this.method = method
                accept(ContentType.Application.Json)
                configure()
            }
                .withLogging()
            readResponse<T>(response)
        }) { cause ->
            raise(
                when (cause) {
                    is SerializationException -> EdiAdapterError.Decoding(cause)
                    else -> EdiAdapterError.Transport(cause)
                }
            )
        }
    }

    private suspend inline fun <reified T> Raise<EdiAdapterError>.readResponse(
        response: HttpResponse
    ): T {
        ensure(response.status.isSuccess()) { apiError(response) }
        return when (T::class) {
            Unit::class -> Unit as T
            else -> json.decodeFromString<T>(response.bodyAsText())
        }
    }

    private suspend fun apiError(response: HttpResponse): EdiAdapterError.Api {
        val problem = Either.catch {
            json.decodeFromString<MshApiProblemDetails>(response.bodyAsText())
        }
            .getOrNull()

        return EdiAdapterError.Api(response.status.value, problem)
    }
}

suspend fun HttpResponse.withLogging(): HttpResponse = apply {
    if (log.isDebugEnabled()) {
        val body = bodyAsText()
        log.debug { "Response from ${request.method} ${request.url} is $status: $body" }
    }
}
