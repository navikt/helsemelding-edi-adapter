package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * An unread message event received through polling or the `data` field of an SSE notification event.
 *
 * Unread notifications do not carry an offset. The unread polling and streaming endpoints select
 * notifications by HER ID without requiring an offset for subsequent requests or stream reconnection.
 *
 * @property notificationId Unique identifier of this notification.
 * @property relatedMessageId Identifier of the associated message, usable with the message endpoints.
 * @property type Kind of event and which aspect of the message changed.
 * @property notificationReceiverHerId HER ID of the communication party this notification is addressed to.
 * @property notificationTriggeredByHerId HER ID of the party whose action caused the event, when supplied.
 * @property description Human-readable explanation of the event.
 * @property createdAt Instant when the notification was created.
 */
@Serializable
data class UnreadNotification(
    val notificationId: Uuid,
    val relatedMessageId: Uuid? = null,
    val type: NotificationType,
    val notificationReceiverHerId: Int,
    val notificationTriggeredByHerId: Int? = null,
    val description: String? = null,
    val createdAt: Instant? = null
)
