package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

@Serializable
data class GetMessagesRequest(
    val receiverHerIds: List<Int>,
    val businessDocumentId: String? = null,
    val includeMetadata: Boolean = false,
    val messagesToFetch: Int = 10,
    val orderBy: OrderBy = OrderBy.ASC
) {
    fun toUrlParams(): String {
        val params = mutableListOf<String>()

        receiverHerIds.forEach {
            params += "receiverHerIds=$it"
        }

        businessDocumentId?.let {
            params += "businessDocumentId=$it"
        }

        params += "includeMetadata=$includeMetadata"
        params += "messagesToFetch=$messagesToFetch"
        params += "orderBy=${orderBy.name}"

        return params.joinToString("&")
    }
}
