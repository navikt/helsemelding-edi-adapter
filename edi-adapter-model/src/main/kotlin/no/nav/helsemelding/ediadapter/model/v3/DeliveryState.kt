package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Transport acknowledgement state for a message recipient.
 *
 * This describes delivery over the transport layer, not acceptance by the receiving application.
 */
@Serializable
enum class DeliveryState {
    /** No transport confirmation is available. */
    @SerialName("Unconfirmed")
    UNCONFIRMED,

    /** Transport delivery was confirmed by an acknowledgement without a severe ebXML error. */
    @SerialName("Acknowledged")
    ACKNOWLEDGED,

    /** Transport delivery was rejected with an ebXML MessageError of severity Error. */
    @SerialName("Rejected")
    REJECTED,

    /** Sending and resending to the EDI recipient did not produce a transport receipt. */
    @SerialName("Abandoned")
    ABANDONED
}
