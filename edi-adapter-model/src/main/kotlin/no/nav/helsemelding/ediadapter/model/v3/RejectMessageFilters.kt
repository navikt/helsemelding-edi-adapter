package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Criteria for rejecting incoming business documents before they reach the recipient.
 *
 * For traditional EDI, message function is matched against the ebXML action. For EDI 2.0 senders, it is
 * matched against [PostMessageRequest.messageTypeIdentificator].
 *
 * @property messageFunction Rejected message function codes from FinnKode 8279. Serialized using the key
 *     `MessageFunction`.
 */
@Serializable
data class RejectMessageFilters(
    @SerialName("MessageFunction")
    val messageFunction: List<String>? = null
)
