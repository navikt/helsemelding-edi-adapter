package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ReceiveNotificationChannel(val value: String) {
    @SerialName("ApiPolling")
    API_POLLING("ApiPolling"),

    @SerialName("Kafka")
    KAFKA("Kafka")
}
