package no.nav.helsemelding.ediadapter.server.model

import kotlinx.serialization.Serializable
import no.nav.helsemelding.ediadapter.model.v3.MessageTransportMetadataOverrides
import no.nav.helsemelding.ediadapter.model.v3.PostMessageRequest
import no.nav.helsemelding.ediadapter.server.config.Nhn

@Serializable
internal data class PostMessageRequest(
    val businessDocument: String,
    val senderHerId: Int,
    val receiverHerIds: List<Int>,
    val contentType: String,
    val contentTransferEncoding: String,
    val messageTypeIdentificator: String,
    val applicationName: String,
    val applicationVersion: String,
    val transportMetadataOverrides: MessageTransportMetadataOverrides? = null
) {
    companion object {
        fun from(request: PostMessageRequest, config: Nhn) = PostMessageRequest(
            businessDocument = request.businessDocument,
            senderHerId = request.senderHerId,
            receiverHerIds = request.receiverHerIds,
            contentType = request.contentType,
            contentTransferEncoding = request.contentTransferEncoding,
            messageTypeIdentificator = request.messageTypeIdentificator,
            applicationName = config.applicationName.value,
            applicationVersion = config.applicationVersion.value,
            transportMetadataOverrides = request.transportMetadataOverrides
        )
    }
}
