package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable

/**
 * Requests generation and sending of an application receipt for a received message.
 *
 * The server builds the receipt XML from this request and the original message. Include errors when
 * rejecting the message.
 *
 * @property appRecSenderHerId HER ID of an original document recipient sending this receipt. Used in the
 *     receipt document and, by default, its envelope.
 * @property appRecStatus Application processing outcome for the original message.
 * @property appRecErrorList Reasons for rejection; must be populated when [appRecStatus] is
 *     [AppRecStatus.REJECTED].
 * @property applicationName Name of the application or electronic patient record system producing the
 *     receipt (HIS 1210:2018).
 * @property applicationVersion Version of the application producing the receipt (HIS 1210:2018).
 * @property transportMetadataOverrides Optional envelope overrides; normally the server derives the values
 *     from the request and original message.
 */
@Serializable
data class PostAppRecRequest(
    val appRecSenderHerId: Int,
    val appRecStatus: AppRecStatus,
    val appRecErrorList: List<AppRecError>? = null,
    val applicationName: String,
    val applicationVersion: String,
    val transportMetadataOverrides: AppRecTransportMetadataOverrides? = null
)
