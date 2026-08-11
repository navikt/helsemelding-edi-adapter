package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Filters used to automatically reject incoming messages for a HER-ID.
 *
 * Part of the experimental NHN EDI v2/vNext API.
 *
 * @property messageFunction list of message function codes that should be rejected (required)
 * @property xmlNamespace list of XML namespaces whose messages should be rejected (required)
 */
@Serializable
data class RejectMessageFilters(
    @SerialName("MessageFunction")
    val messageFunction: List<String>,

    @SerialName("XmlNamespace")
    val xmlNamespace: List<String>
)
