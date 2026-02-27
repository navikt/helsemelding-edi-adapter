package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RejectMessageFilters(
    @SerialName("MessageFunction")
    val messageFunction: List<String>,

    @SerialName("XmlNamespace")
    val xmlNamespace: List<String>
)
