package no.nav.helsemelding.ediadapter.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Error response returned by the EDI Adapter API on failure.
 *
 * @property error a short error message
 * @property errorCode NHN internal error code (required)
 * @property validationErrors list of field-level validation error messages
 * @property stackTrace server-side stack trace, typically only present in non-production environments
 * @property requestId the unique request identifier for tracing (required)
 */
@Serializable
data class ErrorMessage constructor(
    @SerialName("Error")
    val error: String? = null,

    @SerialName("ErrorCode")
    val errorCode: Int,

    @SerialName("ValidationErrors")
    val validationErrors: List<String>? = null,

    @SerialName("StackTrace")
    val stackTrace: String? = null,

    @SerialName("RequestId")
    val requestId: String
)
