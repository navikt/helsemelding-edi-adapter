package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

@Serializable
data class PostMshConfigurationRequest(
    val mshConfigurations: List<MshConfiguration>
)
