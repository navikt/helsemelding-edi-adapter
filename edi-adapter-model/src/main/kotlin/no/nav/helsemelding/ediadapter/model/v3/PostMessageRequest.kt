package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable

/**
 * Business document and addressing metadata submitted to send a new message.
 *
 * NHN documents a maximum request size of 35 MB. The supported payload is XML encoded as Base64.
 *
 * @property businessDocument Base64-encoded bytes of the business document.
 * @property senderHerId HER ID to use as the sender in the transport envelope.
 * @property receiverHerIds HER IDs of the intended transport recipients; provide at least one.
 * @property contentType Media type of the decoded document. The supported value is `application/xml`.
 * @property contentTransferEncoding Encoding applied to [businessDocument]. The supported value is
 *     `base64`.
 * @property messageTypeIdentificator Document type identifier from FinnKode 8279, for example
 *     `DIALOG_HELSEFAGLIG`.
 * @property applicationName Name of the sending application or electronic patient record system (HIS
 *     1210:2018).
 * @property applicationVersion Version of the sending application (HIS 1210:2018).
 * @property transportMetadataOverrides Optional envelope overrides for exchanges requiring metadata beyond
 *     the automatically derived values.
 */
@Serializable
data class PostMessageRequest(
    val businessDocument: String,
    val senderHerId: Int,
    val receiverHerIds: List<Int>,
    val contentType: String,
    val contentTransferEncoding: String,
    val messageTypeIdentificator: String,
    val applicationName: String,
    val applicationVersion: String,
    val transportMetadataOverrides: MessageTransportMetadataOverrides? = null
)
