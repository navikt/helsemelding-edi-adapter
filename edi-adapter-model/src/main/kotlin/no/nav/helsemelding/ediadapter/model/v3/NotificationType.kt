package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Kinds of message updates reported in a [Notification].
 */
@Serializable
enum class NotificationType {
    /** A business document has arrived and is available for download using [Notification.relatedMessageId]. */
    @SerialName("NewMessage")
    NEW_MESSAGE,

    /** An incoming document was refused and is unavailable for download by the recipient. */
    @SerialName("RefusedMessage")
    REFUSED_MESSAGE,

    /** The send state of an outgoing document changed. */
    @SerialName("MessageSentStateUpdated")
    MESSAGE_SENT_STATE_UPDATED,

    /** Application receipt information for an outgoing document was received or updated. */
    @SerialName("MessageApprecInfoUpdated")
    MESSAGE_APPREC_INFO_UPDATED,

    /** The transport delivery state of an outgoing document changed. */
    @SerialName("MessageDeliveryStateUpdated")
    MESSAGE_DELIVERY_STATE_UPDATED
}
