package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable
import no.nav.helsemelding.ediadapter.serializer.FlexibleInstantSerializer
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Metadata for a message retrieved by its EDI message ID.
 *
 * The business document payload is retrieved separately from the document endpoint.
 *
 * @property id EDI message identifier, represented as a UUID, used by the message endpoints.
 * @property senderHerId HER ID of the message sender, when supplied.
 * @property receiverHerIds HER IDs of the message recipients, when supplied.
 * @property businessDocumentId Value of `MsgId` in the business document; distinct from the EDI message
 *     [id].
 * @property businessDocumentGenDate Value of `GenDate` in the business document; dates without a time zone are interpreted in Europe/Oslo.
 * @property businessDocumentMsgType Value of `MsgType` in the business document, for example `SVAR_RTG`.
 * @property contentType Media type of the business document, for example `application/xml`.
 */
@Serializable
data class GetMessageResponse(
    val id: Uuid,
    val senderHerId: Int? = null,
    val receiverHerIds: List<Int>? = null,
    val businessDocumentId: String? = null,
    @Serializable(with = FlexibleInstantSerializer::class)
    val businessDocumentGenDate: Instant? = null,
    val businessDocumentMsgType: String? = null,
    val contentType: String? = null
)
