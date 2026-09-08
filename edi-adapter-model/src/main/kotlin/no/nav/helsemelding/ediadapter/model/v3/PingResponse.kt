package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable

/**
 * Result of a connection check against the message handler.
 *
 * @property response Connection check reply, normally `Pong`.
 * @property timestampUtc UTC timestamp returned by the server, when supplied.
 */
@Serializable
data class PingResponse(
    val response: String,
    val timestampUtc: String? = null
)
