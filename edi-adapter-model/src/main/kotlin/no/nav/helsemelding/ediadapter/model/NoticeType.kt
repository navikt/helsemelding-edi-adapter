package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The type of a notice from the EDI adapter.
 *
 * Part of the experimental NHN EDI v2/vNext API.
 */
@Serializable
enum class NoticeType(val value: String) {
    /** A new message has arrived in the inbox. */
    @SerialName("NewMessage")
    NEW_MESSAGE("NewMessage"),

    /** A previously sent message was refused by the receiver. */
    @SerialName("RefusedMessage")
    REFUSED_MESSAGE("RefusedMessage")
}
