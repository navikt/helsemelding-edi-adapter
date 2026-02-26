package no.nav.helsemelding.ediadapter.model.v2

import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

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
