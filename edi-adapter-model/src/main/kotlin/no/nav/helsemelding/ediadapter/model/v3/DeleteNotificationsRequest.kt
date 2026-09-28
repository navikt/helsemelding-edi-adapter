package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * Requests deletion of notifications that have been read and processed.
 *
 * Use notification IDs from [UnreadNotification] after processing has completed successfully.
 *
 * @property notificationIds Unique identifiers of the notifications to delete, with at most 1000 IDs
 *     per request. These are notification IDs, not the associated message IDs.
 */
@Serializable
data class DeleteNotificationsRequest(
    val notificationIds: List<Uuid>
)
