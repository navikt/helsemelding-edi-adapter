package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable

/**
 * Transport and application processing status for one recipient of a message.
 *
 * Successful transport does not imply that the receiving application accepted the document; consult
 * [apprecInfo] for that result.
 *
 * @property receiverHerId HER ID of the document recipient or copy recipient.
 * @property transportDeliveryState Transport acknowledgement state for this recipient.
 * @property sent Whether the message has been sent to this recipient.
 * @property apprecInfo Application receipt status and errors; null when no application receipt has been
 *     received from the recipient.
 */
@Serializable
data class StatusInfo(
    val receiverHerId: Int,
    val transportDeliveryState: DeliveryState,
    val sent: Boolean,
    val apprecInfo: ApprecInfo? = null
)
