package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable

/**
 * Reference returned when sending an application receipt is accepted.
 *
 * Acceptance of the request does not itself confirm delivery to the recipient.
 *
 * @property id Identifier of the generated receipt message, used to retrieve it or query its status;
 *     distinct from the original message ID.
 */
@Serializable
data class PostApprecResponse(
    val id: String? = null
)
