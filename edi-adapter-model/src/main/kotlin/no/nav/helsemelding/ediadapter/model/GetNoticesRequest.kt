package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

@Serializable
data class GetNoticesRequest(
    val receiverHerIds: List<Int>,
    val messagesToFetch: Int = 10
) {
    fun toUrlParams(): String {
        val params = mutableListOf<String>()

        receiverHerIds.forEach {
            params += "receiverHerIds=$it"
        }

        params += "messagesToFetch=$messagesToFetch"

        return params.joinToString("&")
    }
}
