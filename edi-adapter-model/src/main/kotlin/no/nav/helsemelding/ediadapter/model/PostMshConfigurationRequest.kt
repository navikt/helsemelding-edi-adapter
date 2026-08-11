package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

/**
 * Request body for updating MSH (Message Service Handler) configuration.
 *
 * Part of the experimental NHN EDI v2/vNext API.
 *
 * @property mshConfigurations list of MSH configurations to apply, one per HER-ID (required)
 */
@Serializable
data class PostMshConfigurationRequest(
    val mshConfigurations: List<MshConfiguration>
)
