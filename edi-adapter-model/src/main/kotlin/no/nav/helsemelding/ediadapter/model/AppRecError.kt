package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.Serializable

/**
 * An error entry in an AppRec error list.
 *
 * @property errorCode the error code from the AppRec
 * @property details additional details about the error
 * @property description human-readable description of the error
 * @property oid object identifier for the code system used
 */
@Serializable
data class AppRecError(
    val errorCode: String? = null,
    val details: String? = null,
    val description: String? = null,
    val oid: String? = null
)
