package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

/**
 * AppRec (application receipt) information for a specific receiver.
 *
 * @property receiverHerId the HER-ID of the receiver that processed the message (required)
 * @property appRecStatus the AppRec status reported by the receiver
 * @property appRecErrorList list of errors reported in the AppRec
 */
@Serializable
data class ApprecInfo(
    val receiverHerId: Int,
    val appRecStatus: AppRecStatus? = null,
    val appRecErrorList: List<AppRecError>? = null
)
