package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Metadata returned after successfully sending a message or AppRec.
 *
 * @property id the unique identifier assigned to the sent message (required)
 * @property location the location URI of the 1message (required)
 */
@Serializable
data class Metadata(
    val id: Uuid,
    val location: String
)
