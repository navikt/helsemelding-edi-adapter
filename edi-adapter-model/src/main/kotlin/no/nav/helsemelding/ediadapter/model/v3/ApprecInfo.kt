package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable

/**
 * Application receipt information associated with one recipient of a message.
 *
 * Use [appRecStatus] to determine application acceptance; transport delivery is represented separately by
 * [DeliveryState].
 *
 * @property appRecStatus Outcome reported by the receiving application, when available.
 * @property appRecErrorList Errors included in the application receipt, when present.
 */
@Serializable
data class ApprecInfo(
    val appRecStatus: AppRecStatus? = null,
    val appRecErrorList: List<AppRecError>? = null
)
