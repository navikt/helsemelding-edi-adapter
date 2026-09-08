package no.nav.helsemelding.ediadapter.model.v3

import kotlinx.serialization.Serializable

/**
 * Identifies the recipient marking a business document as downloaded.
 *
 * This records download status; application acceptance is reported separately using [PostAppRecRequest].
 *
 * @property receiverHerId HER ID of the recipient or copy recipient that downloaded the document.
 */
@Serializable
data class MarkAsDownloadedRequest(
    val receiverHerId: Int
)
