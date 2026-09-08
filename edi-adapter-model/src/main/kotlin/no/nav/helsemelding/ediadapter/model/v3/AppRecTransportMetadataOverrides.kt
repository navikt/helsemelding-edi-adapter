package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable

/**
 * Optional overrides for the ebXML envelope of a generated application receipt.
 *
 * Normally addressing is derived from the original message and other envelope values from the applicable
 * standards. Use overrides for exchanges requiring different metadata.
 *
 * @property transportSenderHerId Overrides the envelope sender HER ID; otherwise addressing is derived from
 *     the received business document.
 * @property transportReceiverHerId Overrides the envelope receiver HER ID; otherwise addressing is derived
 *     from the received business document.
 * @property cpaId Overrides the ebXML collaboration agreement ID; otherwise derived according to HIS
 *     1037:2011.
 * @property conversationId Overrides the ebXML conversation ID; otherwise derived according to HIS
 *     1037:2011.
 * @property service Overrides the ebXML service; otherwise derived according to HIS 1209:2018.
 * @property serviceType Overrides the ebXML service type; otherwise derived according to HIS 1209:2018.
 * @property action Overrides the ebXML action; otherwise derived from the business document.
 * @property senderRole Overrides the sender role in the envelope; otherwise derived according to HIS
 *     1209:2018.
 * @property receiverRole Overrides the receiver role in the envelope; otherwise derived according to HIS
 *     1209:2018.
 * @property middlewareName Name of intermediary software between the application and message handler, as
 *     described by HIS 1210:2018.
 * @property middlewareVersion Version of the intermediary software identified by [middlewareName].
 * @property compressPayload When `true`, Gzip-compresses the payload before encryption and inclusion in the
 *     ebXML envelope.
 */
@Serializable
data class AppRecTransportMetadataOverrides(
    val transportSenderHerId: Int? = null,
    val transportReceiverHerId: Int? = null,
    val cpaId: String? = null,
    val conversationId: String? = null,
    val service: String? = null,
    val serviceType: String? = null,
    val action: String? = null,
    val senderRole: String? = null,
    val receiverRole: String? = null,
    val middlewareName: String? = null,
    val middlewareVersion: String? = null,
    val compressPayload: Boolean? = null
)
