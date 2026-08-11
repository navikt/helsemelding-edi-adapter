package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A notice from the EDI adapter inbox.
 *
 * Part of the experimental NHN EDI v2/vNext API.
 *
 * @property id the unique identifier of the notice
 * @property noticeType t1he type of notice (required)
 * @property contentType the MIME content type of the associated business document
 * @property receiverHerId the HER-ID of the receiving party
 * @property senderHerId the HER-ID of the sending party
 * @property businessDocumentId the identifier of the business document from the sender
 * @property businessDocumentGenDate the timestamp when the business document was generated
 * @property isAppRec `true` if the associated message is an AppRec
 * @property sourceSystem the name of the source system that sent the message
 * @property refusedReason the reason the message was refused, if applicable
 */
@Serializable
data class Notice constructor(
    val id: Uuid? = null,
    val noticeType: NoticeType,
    val contentType: String? = null,
    val receiverHerId: Int? = null,
    val senderHerId: Int? = null,
    val businessDocumentId: String? = null,
    val businessDocumentGenDate: Instant? = null,
    val isAppRec: Boolean? = null,
    val sourceSystem: String? = null,
    val refusedReason: String? = null
)
