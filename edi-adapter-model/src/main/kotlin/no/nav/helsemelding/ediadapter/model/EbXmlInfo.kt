package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

/**
 * Optional ebXML message header overrides.
 *
 * All fields are optional. When set, the adapter uses the provided values instead of
 * its own defaults when constructing the ebXML envelope.
 *
 * @property cpaId the Collaboration Protocol Agreement identifier
 * @property conversationId the ebXML conversation identifier
 * @property service the ebXML service name
 * @property serviceType the ebXML service type
 * @property action the ebXML action name
 * @property senderRole the role of the sending party
 * @property useSenderLevel1HerId if `true`, the organisation level HER-ID of the sender is used in the envelope
 * @property receiverRole the role of the receiving party
 * @property applicationName the name of the sending application
 * @property applicationVersion the version of the sending application
 * @property middlewareName the name of the middleware handling the message
 * @property middlewareVersion the version of the middleware
 * @property compressPayload if `true`, the message payload is compressed before sending
 */
@Serializable
data class EbXmlInfo(
    val cpaId: String? = null,
    val conversationId: String? = null,
    val service: String? = null,
    val serviceType: String? = null,
    val action: String? = null,
    val senderRole: String? = null,
    val useSenderLevel1HerId: Boolean? = null,
    val receiverRole: String? = null,
    val applicationName: String? = null,
    val applicationVersion: String? = null,
    val middlewareName: String? = null,
    val middlewareVersion: String? = null,
    val compressPayload: Boolean? = null
)
