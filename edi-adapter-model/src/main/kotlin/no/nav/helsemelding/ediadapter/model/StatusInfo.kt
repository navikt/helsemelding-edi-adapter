package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

/**
 * Delivery and AppRec status for a message, scoped to a single receiver HER-ID.
 *
 * @property receiverHerId the HER-ID of the receiver (required)
 * @property transportDeliveryState the ebXML transport delivery state (required)
 * @property sent `true` if the message has been dispatched to the receiver (required)
 * @property appRecStatus the AppRec status reported by the receiver
 */
@Serializable
data class StatusInfo(
    val receiverHerId: Int,
    val transportDeliveryState: DeliveryState,
    val sent: Boolean,
    val appRecStatus: AppRecStatus? = null
)
