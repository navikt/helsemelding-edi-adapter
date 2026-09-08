package no.nav.helsemelding.ediadapter.server

import arrow.core.raise.Raise
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall

internal fun Raise<ValidationError>.noticeQueryParams(
    call: ApplicationCall
): Parameters {
    val receiverHerIds = receiverHerIds(call)
    val messagesToFetch = messagesToFetch(call)

    return Parameters.build {
        appendAll("ReceiverHerIds", receiverHerIds)
        appendIfPresent("MessagesToFetch", messagesToFetch)
    }
}
