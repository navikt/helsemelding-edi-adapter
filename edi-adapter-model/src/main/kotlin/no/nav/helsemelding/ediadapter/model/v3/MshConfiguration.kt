package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable

/**
 * Message handler settings for one communication party identified by a HER ID.
 *
 * Submit configurations with [SetMshConfigurationsRequest]. A client lock ties the configured party to the
 * requesting HelseID client.
 *
 * @property herId HER ID of the communication party to configure.
 * @property receiveNotificationChannel Channel used to consume notifications. External consumers use
 *     [ReceiveNotificationChannel.API].
 * @property clientLocked When `true`, reserves message operations for the requesting HelseID client; other
 *     clients receive HTTP 423 for locked actions.
 * @property rejectMessageFilters Criteria for rejecting incoming messages before delivery. A null value
 *     configures no rejection filters.
 */
@Serializable
data class MshConfiguration(
    val herId: Int,
    val receiveNotificationChannel: ReceiveNotificationChannel,
    val clientLocked: Boolean? = null,
    val rejectMessageFilters: RejectMessageFilters? = null
)
