package no.nav.helsemelding.ediadapter.server

import io.ktor.http.ContentType.Text.Plain
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.content.TextContent

sealed interface MessageError
sealed interface ValidationError : MessageError

data object MessageIdEmpty : ValidationError
data object MessageIdsMissing : ValidationError
data object ReceiverHerIdsMissing : ValidationError
data object MessageIdsEmpty : ValidationError
data object ReceiverHerIdsEmpty : ValidationError
data object HerIdEmpty : ValidationError
data object SenderHerIdMissing : ValidationError
data object SenderHerIdEmpty : ValidationError
data object BusinessDocumentIdEmpty : ValidationError
data object IncludeMetadataInvalidFormat : ValidationError
data object MessagesToFetchInvalidFormat : ValidationError
data object OrderByInvalidFormat : ValidationError
data object HerIdsMissing : ValidationError
data object HerIdsEmpty : ValidationError
data object HerIdsInvalidFormat : ValidationError
data class HerIdsInvalidCount(val maxItems: Int) : ValidationError
data object OffsetMissing : ValidationError
data object OffsetInvalidFormat : ValidationError
data object NotificationsToFetchInvalidFormat : ValidationError

fun MessageError.toContent(): TextContent =
    when (this) {
        is MessageIdEmpty ->
            TextContent("Message id is empty")

        is MessageIdsMissing ->
            TextContent("Message ids are missing")

        is ReceiverHerIdsMissing ->
            TextContent("Receiver her ids are missing")

        is MessageIdsEmpty ->
            TextContent("Message ids are empty")

        is ReceiverHerIdsEmpty ->
            TextContent("Receiver her ids are empty")

        is HerIdEmpty ->
            TextContent("Her id is empty")

        is SenderHerIdMissing ->
            TextContent("Sender her id is missing")

        is SenderHerIdEmpty ->
            TextContent("Sender her id is empty")

        is BusinessDocumentIdEmpty ->
            TextContent("Business document id is empty")

        is IncludeMetadataInvalidFormat ->
            TextContent("Include metadata must be 'true' or 'false'")

        is MessagesToFetchInvalidFormat ->
            TextContent("Messages to fetch must be a number between 1 and 100")

        is OrderByInvalidFormat ->
            TextContent("Order by must be 1 (Ascending) or 2 (Descending)")

        is HerIdsMissing ->
            TextContent("herIds is required")

        is HerIdsEmpty ->
            TextContent("herIds must not be empty")

        is HerIdsInvalidFormat ->
            TextContent("herIds must contain integers")

        is HerIdsInvalidCount ->
            TextContent("herIds must contain between 1 and $maxItems unique HER IDs")

        is OffsetMissing ->
            TextContent("offset is required")

        is OffsetInvalidFormat ->
            TextContent("offset must be a non-negative 64-bit integer")

        is NotificationsToFetchInvalidFormat ->
            TextContent("notificationsToFetch must be between 1 and 1000")
    }

private fun TextContent(
    content: String,
    statusCode: HttpStatusCode = BadRequest
): TextContent = TextContent(content, Plain, statusCode)
