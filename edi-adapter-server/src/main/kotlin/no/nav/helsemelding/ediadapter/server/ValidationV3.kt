package no.nav.helsemelding.ediadapter.server

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import io.ktor.http.Parameters
import io.ktor.http.parseQueryString
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.queryString
import no.nav.helsemelding.ediadapter.model.v3.DeleteNotificationsRequest

private const val HER_IDS = "herIds"
private const val OFFSET = "offset"
private const val NOTIFICATIONS_TO_FETCH = "notificationsToFetch"

internal fun Raise<ValidationError>.herIds(call: ApplicationCall, maxItems: Int? = null): List<String> =
    herIds(call.request.queryParameters, maxItems)

private fun Raise<ValidationError>.herIds(parameters: Parameters, maxItems: Int?): List<String> {
    val values = ensureNotNull(parameters.getAll(HER_IDS)) { HerIdsMissing }
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
    notificationsToFetch(call.request.queryParameters)

private fun Raise<ValidationError>.notificationsToFetch(parameters: Parameters): Int? =
    parameters[NOTIFICATIONS_TO_FETCH]?.let {
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

internal fun Raise<ValidationError>.unreadNotificationParameters(call: ApplicationCall, stream: Boolean = false): Parameters {
    val parameters = parseQueryString(call.request.queryString(), limit = Int.MAX_VALUE)
    val herIds = herIds(parameters, maxItems = 1500)
    val count = if (stream) null else notificationsToFetch(parameters)
    return Parameters.build {
        appendAll("HerIds", herIds)
        count?.let { append("NotificationsToFetch", it.toString()) }
    }
}

internal fun Raise<ValidationError>.validateDeleteNotifications(request: DeleteNotificationsRequest) {
    val ids = request.notificationIds
    ensure(ids.size <= 1000 && ids.distinct().size == ids.size) { NotificationIdsInvalidCount }
}
