package no.nav.helsemelding.ediadapter.server

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.ktor.http.ParametersBuilder
import io.ktor.server.application.ApplicationCall

private const val MESSAGE_ID = "messageId"
private const val RECEIVER_HER_IDS = "receiverHerIds"
private const val MESSAGES_TO_FETCH = "messagesToFetch"

fun Raise<ValidationError>.messageId(call: ApplicationCall): String =
    requiredPathParam(call, MESSAGE_ID, MessageIdEmpty)

fun Raise<ValidationError>.receiverHerIds(call: ApplicationCall): List<String> =
    ensureNotNull(call.request.queryParameters.getAll(RECEIVER_HER_IDS)) { ReceiverHerIdsMissing }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .also { ensure(it.isNotEmpty()) { ReceiverHerIdsEmpty } }

fun Raise<ValidationError>.messagesToFetch(call: ApplicationCall): Int? =
    call.request.queryParameters[MESSAGES_TO_FETCH]
        ?.trim()
        ?.also { ensure(it.isNotEmpty()) { MessagesToFetchInvalidFormat } }
        ?.toIntOrNull()
        ?.also { ensure(it in 1..100) { MessagesToFetchInvalidFormat } }

internal fun Raise<ValidationError>.requiredPathParam(
    call: ApplicationCall,
    name: String,
    emptyError: ValidationError
): String =
    ensureNotNull(call.parameters[name]) { emptyError }
        .also { ensure(it.isNotBlank()) { emptyError } }

internal fun ParametersBuilder.appendIfPresent(name: String, value: Any?) =
    value?.let { append(name, it.toString()) }
