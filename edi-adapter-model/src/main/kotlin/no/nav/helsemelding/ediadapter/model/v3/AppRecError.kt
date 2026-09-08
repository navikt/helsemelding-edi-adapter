package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable

/**
 * An error reported by the receiving application in an application receipt.
 *
 * @property errorCode Code identifying the application error, for example `E10`.
 *     See [code system 8221](https://finnkode.helsedirektoratet.no/adm/collections/8221?q=8221).
 * @property details Additional information about this particular error; optional when sending a receipt.
 * @property description Meaning of the error code as defined by its code system.
 * @property oid Object identifier of the code system that defines [errorCode].
 */
@Serializable
data class AppRecError(
    val errorCode: String?,
    val details: String?,
    val description: String? = null,
    val oid: String? = null
)
