package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable

/**
 * Error response used by V3 endpoints for request failures.
 *
 * Contains HTTP problem details and diagnostic fields. Fields may be absent depending on the error and the
 * server that produced the response.
 *
 * @property type URI reference identifying the problem category, when supplied.
 * @property title Short description of the problem category.
 * @property status HTTP status code associated with the error.
 * @property detail Explanation specific to this failure; internal details may be omitted.
 * @property instance Reference to the failing request or problem occurrence, typically the endpoint path.
 * @property errorCode Application-specific error code; not necessarily the same as [status].
 * @property requestId Request identifier used to correlate the failure with server logs.
 * @property validationErrors Descriptions of request validation failures, when present.
 * @property stackTrace Diagnostic stack trace, when supplied; NHN documents this as omitted in production.
 * @property timestamp Server-provided time of the error, preserved as a string.
 */
@Serializable
data class MshApiProblemDetails(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val errorCode: Int? = null,
    val requestId: String? = null,
    val validationErrors: List<String>? = null,
    val stackTrace: String? = null,
    val timestamp: String? = null
)
