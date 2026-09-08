package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable

/**
 * A message event received through polling or the `data` field of an SSE notification event.
 *
 * Offsets are global, so gaps between notifications are expected. Persist the offset after successful
 * processing to resume without skipping unprocessed notifications.
 *
 * @property relatedMessageId Identifier of the associated message, usable with the message endpoints.
 * @property type Kind of event and which aspect of the message changed.
 * @property notificationReceiverHerId HER ID of the communication party this notification is addressed to.
 * @property notificationTriggeredByHerId HER ID of the party whose action caused the event, when supplied.
 * @property description Human-readable explanation of the event.
 * @property createdAt Creation timestamp with date/time offset, preserved as a string.
 * @property offset Global notification position used as the offset for subsequent polling or stream
 *     reconnection.
 */
@Serializable
data class Notification(
    val relatedMessageId: String? = null,
    val type: NotificationType,
    val notificationReceiverHerId: Int,
    val notificationTriggeredByHerId: Int? = null,
    val description: String? = null,
    val createdAt: String? = null,
    val offset: Long
)
