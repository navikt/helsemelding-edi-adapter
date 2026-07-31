package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Transport delivery state for an EDI message.
 *
 * @property value the string name used by the NHN EDI API
 * @property description human-readable description
 */
@Serializable
enum class DeliveryState(val value: String, val description: String) {
    /** Transport has not yet been confirmed by the MSH. */
    @SerialName("Unconfirmed")
    UNCONFIRMED("Unconfirmed", "Transport is not confirmed"),

    /** Transport was confirmed — equivalent to ebXML `Acknowledgement` without a severe error list. */
    @SerialName("Acknowledged")
    ACKNOWLEDGED("Acknowledged", "Transport is confirmed. Equivalent to 'Acknowledgement' without severe 'eb:ErrorList/eb:HighestSeverity'"),

    /** Transport was rejected — equivalent to ebXML `MessageError` with `HighestSeverity="Error"`. */
    @SerialName("Rejected")
    REJECTED("Rejected", "Transport is rejected. Equivalent to 'MessageError' with 'eb:ErrorList/eb:HighestSeverity'=\"Error\""),

    /** The received value is not a recognised delivery state. */
    @SerialName("Unknown")
    UNKNOWN("Unknown", "The value is not supported");

    companion object {
        fun fromValue(value: String?): DeliveryState =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}
