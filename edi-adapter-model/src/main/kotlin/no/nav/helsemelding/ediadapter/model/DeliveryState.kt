package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DeliveryState(val value: String, val description: String) {
    @SerialName("Unconfirmed")
    UNCONFIRMED("Unconfirmed", "Transport is not confirmed"),

    @SerialName("Acknowledged")
    ACKNOWLEDGED("Acknowledged", "Transport is confirmed. Equivalent to 'Acknowledgement' without severe 'eb:ErrorList/eb:HighestSeverity'"),

    @SerialName("Rejected")
    REJECTED("Rejected", "Transport is rejected. Equivalent to 'MessageError' with 'eb:ErrorList/eb:HighestSeverity'=\"Error\""),

    @SerialName("Unknown")
    UNKNOWN("Unknown", "The value is not supported");

    companion object {
        fun fromValue(value: String?): DeliveryState =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}
