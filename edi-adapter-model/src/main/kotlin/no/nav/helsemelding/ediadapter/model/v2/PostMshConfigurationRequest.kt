package no.nav.helsemelding.ediadapter.model.v2

import kotlinx.serialization.Serializable

@Serializable
data class PostMshConfigurationRequest(
    val mshConfigurations: List<MshConfiguration>
)
