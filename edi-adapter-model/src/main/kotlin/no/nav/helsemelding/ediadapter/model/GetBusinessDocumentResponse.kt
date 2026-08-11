package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

/**
 * Response containing the raw XML payload of an EDI message.
 *
 * @property businessDocument the raw XML content of the business document (required)
 * @property contentType the MIME content type of the document, e.g. `"application/xml"` (required)
 * @property contentTransferEncoding the content transfer encoding, e.g. `"base64"` (required)
 */
@Serializable
data class GetBusinessDocumentResponse(
    val businessDocument: String,
    val contentType: String,
    val contentTransferEncoding: String
)
