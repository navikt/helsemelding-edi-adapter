package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable

/**
 * Delivery and application receipt information returned by the message status endpoint.
 *
 * @property statusList Status entries for the message recipients, when supplied.
 */
@Serializable
data class GetStatusResponse(
    val statusList: List<StatusInfo>? = null
)
