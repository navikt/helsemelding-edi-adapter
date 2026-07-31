package no.nav.helsemelding.ediadapter.client

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.contentType
import no.nav.helsemelding.ediadapter.model.ApprecInfo
import no.nav.helsemelding.ediadapter.model.ErrorMessage
import no.nav.helsemelding.ediadapter.model.GetBusinessDocumentResponse
import no.nav.helsemelding.ediadapter.model.GetMessagesRequest
import no.nav.helsemelding.ediadapter.model.GetNoticesRequest
import no.nav.helsemelding.ediadapter.model.Message
import no.nav.helsemelding.ediadapter.model.Metadata
import no.nav.helsemelding.ediadapter.model.Notice
import no.nav.helsemelding.ediadapter.model.PostAppRecRequest
import no.nav.helsemelding.ediadapter.model.PostMessageRequest
import no.nav.helsemelding.ediadapter.model.PostMshConfigurationRequest
import no.nav.helsemelding.ediadapter.model.StatusInfo
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/**
 * Client for communicating with the EDI Adapter service.
 *
 * Provides access to EDI messages, AppRec handling, and MSH configuration
 * through the NHN EDI API.
 *
 * All operations return [Either] where [Either.Left] contains an [ErrorMessage]
 * on failure, and [Either.Right] contains the successful result.
 *
 */
interface EdiAdapterClient {
    /**
     * Retrieves AppRec (application receipt) information for a message.
     *
     * @param id the unique identifier of the message to look up AppRec info for
     * @return a list of [ApprecInfo] entries, one per receiver HER-ID, or an [ErrorMessage] on failure
     */
    suspend fun getApprecInfo(id: Uuid): Either<ErrorMessage, List<ApprecInfo>>

    /**
     * Retrieves messages matching the given filter criteria.
     *
     * @param getMessagesRequest filter and pagination parameters for the query
     * @return a list of matching [Message] objects, or an [ErrorMessage] on failure
     */
    suspend fun getMessages(getMessagesRequest: GetMessagesRequest): Either<ErrorMessage, List<Message>>

    /**
     * Retrieves notices for the given receiver HER-IDs.
     *
     * This function is part of the experimental NHN EDI v2/vNext API and may
     * change or be removed without prior notice.
     *
     * @param getNoticesRequest filter parameters including receiver HER-IDs and message count
     * @return a list of [Notice] objects, or an [ErrorMessage] on failure
     */
    @ExperimentalEdiAdapterApi
    suspend fun getNotices(getNoticesRequest: GetNoticesRequest): Either<ErrorMessage, List<Notice>>

    /**
     * Sends a new message to the adapter.
     *
     * @param postMessagesRequest the message payload, content type, encoding, and optional overrides
     * @return [Metadata] with the assigned message ID and storage location, or an [ErrorMessage] on failure
     */
    suspend fun postMessage(postMessagesRequest: PostMessageRequest): Either<ErrorMessage, Metadata>

    /**
     * Retrieves a single message by its unique identifier.
     *
     * @param id the unique identifier of the message
     * @return the [Message], or an [ErrorMessage] if not found or on failure
     */
    suspend fun getMessage(id: Uuid): Either<ErrorMessage, Message>

    /**
     * Retrieves the raw XML payload for a message.
     *
     * @param id the unique identifier of the message
     * @return a [GetBusinessDocumentResponse] containing the document, content type, and encoding,
     *   or an [ErrorMessage] on failure
     */
    suspend fun getBusinessDocument(id: Uuid): Either<ErrorMessage, GetBusinessDocumentResponse>

    /**
     * Retrieves the delivery and AppRec status for a message, per receiver HER-ID.
     *
     * @param id the unique identifier of the message
     * @return a list of [StatusInfo] entries, one per receiver, or an [ErrorMessage] on failure
     */
    suspend fun getMessageStatus(id: Uuid): Either<ErrorMessage, List<StatusInfo>>

    /**
     * Sends an AppRec (application receipt) for a received message.
     *
     * @param id the unique identifier of the message to acknowledge
     * @param apprecSenderHerId the HER-ID of the party sending the AppRec
     * @param postAppRecRequest the AppRec status, optional error list, and optional ebXML overrides
     * @return [Metadata] with the assigned AppRec message ID and location, or an [ErrorMessage] on failure
     */
    suspend fun postApprec(
        id: Uuid,
        apprecSenderHerId: Int,
        postAppRecRequest: PostAppRecRequest
    ): Either<ErrorMessage, Metadata>

    /**
     * Marks a message as read for the given HER-ID.
     *
     * @param id the unique identifier of the message to mark as read
     * @param herId the HER-ID of the receiver marking the message as read
     * @return `true` on success (HTTP 204), or an [ErrorMessage] on failure
     */
    suspend fun markMessageAsRead(id: Uuid, herId: Int): Either<ErrorMessage, Boolean>

    /**
     * Sends MSH (Message Service Handler) configuration.
     *
     * This function is part of the experimental NHN EDI v2/vNext API and may
     * change or be removed without prior notice.
     *
     * @param postMshConfigurationRequest the MSH configuration to apply
     * @return [Unit] on success (HTTP 204), or an [ErrorMessage] on failure
     */
    @ExperimentalEdiAdapterApi
    suspend fun postMshConfiguration(postMshConfigurationRequest: PostMshConfigurationRequest): Either<ErrorMessage, Unit>

    /**
     * Closes the underlying HTTP client and releases resources.
     *
     * Should be called when the client is no longer needed to free connections.
     */
    fun close()
}

/**
 * HTTP-based implementation of [EdiAdapterClient].
 *
 * Communicates with the EDI Adapter service over HTTP using the provided [HttpClient].
 * Use [scopedAuthHttpClient] to create a pre-configured client with Azure AD bearer token support.
 *
 * @param clientProvider factory function that creates the underlying [HttpClient]
 * @param ediAdapterUrl base URL of the EDI Adapter service; defaults to the value from
 *   the `edi-adapter-client.conf` configuration file
 */
class HttpEdiAdapterClient(
    clientProvider: () -> HttpClient,
    private val ediAdapterUrl: String = config().ediAdapterServer.url.toString()
) : EdiAdapterClient {
    private var httpClient = clientProvider.invoke()

    /**
     * Retrieves AppRec (application receipt) information for a message.
     *
     * @param id the unique identifier of the message to look up AppRec info for
     * @return a list of [ApprecInfo] entries, one per receiver HER-ID, or an [ErrorMessage] on failure
     */
    override suspend fun getApprecInfo(id: Uuid): Either<ErrorMessage, List<ApprecInfo>> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/apprec"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return handleResponse(response)
    }

    /**
     * Retrieves messages matching the given filter criteria.
     *
     * @param getMessagesRequest filter and pagination parameters for the query
     * @return a list of matching [Message] objects, or an [ErrorMessage] on failure
     */
    override suspend fun getMessages(getMessagesRequest: GetMessagesRequest): Either<ErrorMessage, List<Message>> {
        val url = "$ediAdapterUrl/api/v1/messages?${getMessagesRequest.toUrlParams()}"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return handleResponse(response)
    }

    /**
     * Retrieves notices for the given receiver HER-IDs.
     *
     * This function is part of the experimental NHN EDI v2/vNext API and may
     * change or be removed without prior notice.
     *
     * @param getNoticesRequest filter parameters including receiver HER-IDs and message count
     * @return a list of [Notice] objects, or an [ErrorMessage] on failure
     */
    @ExperimentalEdiAdapterApi
    override suspend fun getNotices(getNoticesRequest: GetNoticesRequest): Either<ErrorMessage, List<Notice>> {
        val url = "$ediAdapterUrl/api/v2/messages/notices?${getNoticesRequest.toUrlParams()}"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return handleResponse(response)
    }

    /**
     * Sends a new message to the adapter.
     *
     * @param postMessagesRequest the message payload, content type, encoding, and optional overrides
     * @return [Metadata] with the assigned message ID and storage location, or an [ErrorMessage] on failure
     */
    override suspend fun postMessage(postMessagesRequest: PostMessageRequest): Either<ErrorMessage, Metadata> {
        val url = "$ediAdapterUrl/api/v1/messages"
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(postMessagesRequest)
        }.withLogging()

        return handleResponse(response)
    }

    /**
     * Retrieves a single message by its unique identifier.
     *
     * @param id the unique identifier of the message
     * @return the [Message], or an [ErrorMessage] if not found or on failure
     */
    override suspend fun getMessage(id: Uuid): Either<ErrorMessage, Message> {
        val url = "$ediAdapterUrl/api/v1/messages/$id"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return handleResponse(response)
    }

    /**
     * Retrieves the raw XML payload for a message.
     *
     * @param id the unique identifier of the message
     * @return a [GetBusinessDocumentResponse] containing the document, content type, and encoding,
     *   or an [ErrorMessage] on failure
     */
    override suspend fun getBusinessDocument(id: Uuid): Either<ErrorMessage, GetBusinessDocumentResponse> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/document"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return handleResponse(response)
    }

    /**
     * Retrieves the delivery and AppRec status for a message, per receiver HER-ID.
     *
     * @param id the unique identifier of the message
     * @return a list of [StatusInfo] entries, one per receiver, or an [ErrorMessage] on failure
     */
    override suspend fun getMessageStatus(id: Uuid): Either<ErrorMessage, List<StatusInfo>> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/status"
        val response = httpClient.get(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return handleResponse(response)
    }

    /**
     * Sends an AppRec (application receipt) for a received message.
     *
     * @param id the unique identifier of the message to acknowledge
     * @param apprecSenderHerId the HER-ID of the party sending the AppRec
     * @param postAppRecRequest the AppRec status, optional error list, and optional ebXML overrides
     * @return [Metadata] with the assigned AppRec message ID and location, or an [ErrorMessage] on failure
     */
    override suspend fun postApprec(
        id: Uuid,
        apprecSenderHerId: Int,
        postAppRecRequest: PostAppRecRequest
    ): Either<ErrorMessage, Metadata> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/apprec/$apprecSenderHerId"
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(postAppRecRequest)
        }.withLogging()

        return handleResponse(response)
    }

    /**
     * Marks a message as read for the given HER-ID.
     *
     * @param id the unique identifier of the message to mark as read
     * @param herId the HER-ID of the receiver marking the message as read
     * @return `true` on success (HTTP 204), or an [ErrorMessage] on failure
     */
    override suspend fun markMessageAsRead(id: Uuid, herId: Int): Either<ErrorMessage, Boolean> {
        val url = "$ediAdapterUrl/api/v1/messages/$id/read/$herId"
        val response = httpClient.put(url) {
            contentType(ContentType.Application.Json)
        }.withLogging()

        return if (response.status == NoContent) {
            Right(true)
        } else {
            Left(response.body())
        }
    }

    /**
     * Sends MSH (Message Service Handler) configuration.
     *
     * This function is part of the experimental NHN EDI v2/vNext API and may
     * change or be removed without prior notice.
     *
     * @param postMshConfigurationRequest the MSH configuration to apply
     * @return [Unit] on success (HTTP 204), or an [ErrorMessage] on failure
     */
    @ExperimentalEdiAdapterApi
    override suspend fun postMshConfiguration(postMshConfigurationRequest: PostMshConfigurationRequest): Either<ErrorMessage, Unit> {
        val url = "$ediAdapterUrl/api/v2/mshConfiguration"
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(postMshConfigurationRequest)
        }.withLogging()

        return if (response.status == NoContent) {
            Right(Unit)
        } else {
            Left(response.body())
        }
    }

    /**
     * Closes the underlying HTTP client and releases resources.
     *
     * Should be called when the client is no longer needed to free connections.
     */
    override fun close() = httpClient.close()

    private suspend inline fun <reified T> handleResponse(httpResponse: HttpResponse): Either<ErrorMessage, T> {
        return if (httpResponse.status == HttpStatusCode.OK || httpResponse.status == HttpStatusCode.Created) {
            Right(httpResponse.body())
        } else {
            Left(httpResponse.body())
        }
    }
}

suspend fun HttpResponse.withLogging(): HttpResponse {
    val body = this.bodyAsText()
    log.debug { "Response from ${request.method} ${request.url} is $status: $body" }
    return this
}
