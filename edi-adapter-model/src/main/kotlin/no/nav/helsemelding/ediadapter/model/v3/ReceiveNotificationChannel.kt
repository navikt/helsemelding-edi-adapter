package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Selects how a communication party consumes message notifications.
 */
@Serializable
enum class ReceiveNotificationChannel {
    /** Notifications are consumed through the HTTP API, using polling or SSE. Use this channel for external clients. */
    @SerialName("Api")
    API
}
