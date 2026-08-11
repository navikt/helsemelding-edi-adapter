package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

/**
 * MSH (Message Service Handler) configuration for a single HER-ID.
 *
 * @property herId the HER-ID this configuration applies to (required)
 * @property receiveNotificationChannel the channel used to receive new-message notifications (required)
 * @property receiveRefusedMessageNotices whether to receive notices for refused messages (required)
 * @property rejectMessageFilters filters that determine which messages are automatically rejected
 */
@Serializable
data class MshConfiguration(
    val herId: Int,
    val receiveNotificationChannel: ReceiveNotificationChannel,
    val receiveRefusedMessageNotices: Boolean,
    val rejectMessageFilters: RejectMessageFilters? = null
)
