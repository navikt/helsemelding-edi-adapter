package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

/**
 * Request parameters for fetching EDI messages.
 *
 * @property receiverHerIds list of HER-IDs to fetch messages for (required)
 * @property businessDocumentId filter messages by business document ID
 * @property includeMetadata whether to include message metadata in the response; defaults to `false`
 * @property messagesToFetch maximum number of messages to return; defaults to `10`
 * @property orderBy sort order of the returned messages; defaults to [OrderBy.ASC]
 */
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
