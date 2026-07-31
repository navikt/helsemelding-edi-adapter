package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

/**
 * Request parameters for fetching notices (varslinger).
 *
 * Part of the experimental NHN EDI v2/vNext API.
 *
 * @property receiverHerIds list of HER-IDs to fetch notices for (required)
 * @property messagesToFetch maximum number of notices to return; defaults to `10`
 */
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
