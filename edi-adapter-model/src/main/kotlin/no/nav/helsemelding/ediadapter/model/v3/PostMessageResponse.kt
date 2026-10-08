package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Reference returned when a request to send a business document is accepted.
 *
 * Use the reference to retrieve message information and status; acceptance does not guarantee successful
 * delivery.
 *
 * @property id EDI shipment/message reference used by the message endpoints, when supplied.
 */
@Serializable
data class PostMessageResponse(
    val id: Uuid? = null
)
