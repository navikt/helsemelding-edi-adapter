package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NoticeType(val value: String) {
    @SerialName("NewMessage")
    NEW_MESSAGE("NewMessage"),

    @SerialName("RefusedMessage")
    REFUSED_MESSAGE("RefusedMessage")
}
