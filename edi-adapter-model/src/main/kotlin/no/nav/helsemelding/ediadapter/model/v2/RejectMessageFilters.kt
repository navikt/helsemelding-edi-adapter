package no.nav.helsemelding.ediadapter.model.v2

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RejectMessageFilters(
    @SerialName("MessageFunction")
    val messageFunction: List<String>,

    @SerialName("XmlNamespace")
    val xmlNamespace: List<String>
)
