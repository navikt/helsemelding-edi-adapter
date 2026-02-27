package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

@Serializable
data class MshConfiguration(
    val herId: Int,
    val receiveNotificationChannel: ReceiveNotificationChannel,
    val receiveRefusedMessageNotices: Boolean,
    val rejectMessageFilters: RejectMessageFilters? = null
)
