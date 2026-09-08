package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable

/**
 * Creates or updates message handler settings for the supplied communication parties.
 *
 * @property configurations Configurations to apply, each describing one HER ID.
 */
@Serializable
data class SetMshConfigurationsRequest(
    val configurations: List<MshConfiguration>
)
