package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable

/**
 * A batch of unread notifications returned by unread notification polling.
 *
 * The batch size is controlled by `notificationsToFetch`. Unread notifications do not carry an offset;
 * subsequent requests use the HER IDs without an offset.
 *
 * @property unreadNotifications Unread notifications returned for the requested HER IDs. An empty list
 *     means this response contains no unread notifications.
 */
@Serializable
data class GetUnreadNotificationsResponse(
    val unreadNotifications: List<UnreadNotification>
)
