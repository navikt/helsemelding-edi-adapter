package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

/**
 * Request body for sending a new EDI message.
 *
 * @property businessDocument the raw XML content of the business document (required)
 * @property contentType the MIME content type of the document, e.g. `"application/xml"` (required)
 * @property contentTransferEncoding the content transfer encoding, e.g. `"base64"` (required)
 * @property ebXmlOverrides optional overrides for ebXML header fields
 * @property receiverHerIdsSubset subset of receiver HER-IDs to send to; sends to all receivers specified in the message if not set
 */
@Serializable
data class PostMessageRequest(
    val businessDocument: String,
    val contentType: String,
    val contentTransferEncoding: String,
    val ebXmlOverrides: EbXmlInfo? = null,
    val receiverHerIdsSubset: List<Int>? = null
)
