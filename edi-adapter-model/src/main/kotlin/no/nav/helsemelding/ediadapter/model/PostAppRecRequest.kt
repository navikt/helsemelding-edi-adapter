package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

/**
 * Request body for sending an AppRec (application receipt) for a received message.
 *
 * @property appRecStatus the AppRec status to report (required)
 * @property appRecErrorList list of errors to include in the AppRec
 * @property ebXmlOverrides optional overrides for ebXML header fields
 */
@Serializable
data class PostAppRecRequest(
    val appRecStatus: AppRecStatus,
    val appRecErrorList: List<AppRecError>? = null,
    val ebXmlOverrides: EbXmlInfo? = null
)
