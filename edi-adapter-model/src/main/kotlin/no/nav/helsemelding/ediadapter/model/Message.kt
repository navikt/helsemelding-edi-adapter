package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable
import no.nav.helsemelding.ediadapter.serializer.FlexibleInstantSerializer
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * An EDI message.3
 *
 * @property id the unique identifier of the message
 * @property contentType the MIME content type of the business document
 * @property receiverHerId the HER-ID of the receiving party
 * @property senderHerId the HER-ID of the sending party
 * @property businessDocumentId the identifier of the business document from the sender
 * @property businessDocumentGenDate the timestamp when the business document was generated
 * @property isAppRec `true` if the message is an AppRec (application receipt)
 * @property sourceSystem the name of the source system that sent the message
 */
@Serializable
data class Message constructor(
    val id: Uuid? = null,
    val contentType: String? = null,
    val receiverHerId: Int? = null,
    val senderHerId: Int? = null,
    val businessDocumentId: String? = null,
    @Serializable(with = FlexibleInstantSerializer::class)
    val businessDocumentGenDate: Instant? = null,
    val isAppRec: Boolean? = null,
    val sourceSystem: String? = null
)
