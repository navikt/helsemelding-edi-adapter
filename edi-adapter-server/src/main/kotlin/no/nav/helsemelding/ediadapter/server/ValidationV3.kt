package no.nav.helsemelding.ediadapter.server

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall

private const val HER_IDS = "herIds"
private const val OFFSET = "offset"
private const val NOTIFICATIONS_TO_FETCH = "notificationsToFetch"

internal fun Raise<ValidationError>.herIds(call: ApplicationCall, maxItems: Int? = null): List<String> {
    val values = ensureNotNull(call.request.queryParameters.getAll(HER_IDS)) { HerIdsMissing }
    val herIds = values.map { it.trim().toIntOrNull() ?: raise(HerIdsInvalidFormat) }
    ensure(herIds.isNotEmpty()) { HerIdsEmpty }
    if (maxItems != null) {
        ensure(herIds.size <= maxItems && herIds.distinct().size == herIds.size) { HerIdsInvalidCount(maxItems) }
    }
    return herIds.map { it.toString() }
}

internal fun Raise<ValidationError>.offset(call: ApplicationCall): Long? =
    call.request.queryParameters[OFFSET]?.let {
        val offset = it.trim().toLongOrNull() ?: raise(OffsetInvalidFormat)
        ensure(offset >= 0) { OffsetInvalidFormat }
        offset
    }

internal fun Raise<ValidationError>.notificationsToFetch(call: ApplicationCall): Int? =
    call.request.queryParameters[NOTIFICATIONS_TO_FETCH]?.let {
        val count = it.trim().toIntOrNull() ?: raise(NotificationsToFetchInvalidFormat)
        ensure(count in 1..1000) { NotificationsToFetchInvalidFormat }
        count
    }

internal fun Raise<ValidationError>.notificationParameters(call: ApplicationCall, stream: Boolean = false): Parameters {
    val herIds = herIds(call, maxItems = 1500)
    val offset = offset(call)
    if (!stream) ensureNotNull(offset) { OffsetMissing }
    val count = if (stream) null else notificationsToFetch(call)
    return Parameters.build {
        appendAll("HerIds", herIds)
        offset?.let { append("Offset", it.toString()) }
        count?.let { append("NotificationsToFetch", it.toString()) }
    }
}
