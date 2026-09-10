package no.nav.helsemelding.ediadapter.client

import no.nav.helsemelding.ediadapter.model.v3.MshApiProblemDetails

/** Errors returned by [EdiAdapterClient] for HTTP requests and notification streams. */
sealed interface EdiAdapterError {
    /**
     * The API returned a non-successful HTTP status.
     *
     * @property status HTTP status code returned by the API.
     * @property problem Problem details, or `null` if the response body could not be read or decoded.
     */
    data class Api(val status: Int, val problem: MshApiProblemDetails? = null) : EdiAdapterError

    /**
     * The request or response stream could not be completed.
     * The server may still have processed the request.
     *
     * @property cause The underlying exception.
     */
    data class Transport(val cause: Throwable) : EdiAdapterError

    /**
     * A payload could not be serialized or deserialized, or an SSE response had an unexpected format.
     *
     * @property cause The serialization or SSE protocol exception.
     */
    data class Decoding(val cause: Throwable) : EdiAdapterError
}
