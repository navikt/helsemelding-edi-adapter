package no.nav.helsemelding.ediadapter.server.model

import kotlinx.serialization.Serializable
import no.nav.helsemelding.ediadapter.model.v3.AppRecError
import no.nav.helsemelding.ediadapter.model.v3.AppRecStatus
import no.nav.helsemelding.ediadapter.model.v3.AppRecTransportMetadataOverrides
import no.nav.helsemelding.ediadapter.model.v3.PostAppRecRequest
import no.nav.helsemelding.ediadapter.server.config.Nhn

@Serializable
internal data class PostAppRecRequest(
    val appRecSenderHerId: Int,
    val appRecStatus: AppRecStatus,
    val appRecErrorList: List<AppRecError>? = null,
    val applicationName: String,
    val applicationVersion: String,
    val transportMetadataOverrides: AppRecTransportMetadataOverrides? = null
) {
    companion object {
        fun from(request: PostAppRecRequest, config: Nhn) = PostAppRecRequest(
            appRecSenderHerId = request.appRecSenderHerId,
            appRecStatus = request.appRecStatus,
            appRecErrorList = request.appRecErrorList,
            applicationName = config.applicationName.value,
            applicationVersion = config.applicationVersion.value,
            transportMetadataOverrides = request.transportMetadataOverrides
        )
    }
}
