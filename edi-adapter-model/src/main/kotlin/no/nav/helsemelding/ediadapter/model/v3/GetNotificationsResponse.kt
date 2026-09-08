package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable

/**
 * A batch of notifications returned by notification polling.
 *
 * Use the notification offsets to resume polling; offsets are global and may contain gaps.
 *
 * @property notifications Notifications returned for the requested HER IDs and offset. An empty list means
 *     this response contains no notifications.
 */
@Serializable
data class GetNotificationsResponse(
    val notifications: List<Notification>
)
