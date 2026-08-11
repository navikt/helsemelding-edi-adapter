package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Notification channel used by the MSH to deliver new-message notifications.
 *
 * Part of the experimental NHN EDI v2/vNext API.
 */
@Serializable
enum class ReceiveNotificationChannel(val value: String) {
    /** Notifications are delivered via API polling. */
    @SerialName("ApiPolling")
    API_POLLING("ApiPolling"),

    /** Notifications are delivered via Kafka. */
    @SerialName("Kafka")
    KAFKA("Kafka")
}
