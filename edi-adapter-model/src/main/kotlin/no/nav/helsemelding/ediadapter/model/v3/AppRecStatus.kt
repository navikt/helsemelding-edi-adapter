package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Application processing outcomes defined by HIS 80415, code system OID 8258.
 *
 * These describe application acceptance, separately from transport delivery in [DeliveryState].
 */
@Serializable
enum class AppRecStatus {
    /** The receiving application accepted the message. Serialized as `Ok`. */
    @SerialName("Ok")
    OK,

    /** The receiving application rejected the message. Include rejection reasons in [PostAppRecRequest.appRecErrorList]. */
    @SerialName("Rejected")
    REJECTED,

    /** The message was accepted with an error in a submessage. Listed by NHN but documented as not supported. */
    @SerialName("OkErrorInMessagePart")
    OK_ERROR_IN_MESSAGE_PART
}
