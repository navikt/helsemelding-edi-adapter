package no.nav.helsemelding.ediadapter.server.plugin

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NoContent
import io.ktor.http.HttpStatusCode.Companion.OK
import no.nav.helsemelding.ediadapter.model.v2.MshConfiguration
import no.nav.helsemelding.ediadapter.model.v2.Notice
import no.nav.helsemelding.ediadapter.model.v2.NoticeType
import no.nav.helsemelding.ediadapter.model.v2.PostMshConfigurationRequest
import no.nav.helsemelding.ediadapter.model.v2.ReceiveNotificationChannel
import no.nav.helsemelding.ediadapter.model.v2.RejectMessageFilters
import kotlin.time.Instant
import kotlin.uuid.Uuid

object MessagesApiV2 {

    /* =============================================================
     * GET /messages/notices
     * ============================================================= */

    const val GET_NOTICES = "/messages/notices"

    val getNoticesDocs: RouteConfig.() -> Unit = {
        summary = "Get a list of message notices"
        description = "Get a list of message notices for the given receiver HER IDs."

        request {
            queryParameter<List<Int>>("receiverHerIds") {
                description = "List of receiver HER IDs"
                required = true

                example("Multiple receivers") {
                    summary = "Multiple receiver HER IDs"
                    description = "At least one receiver HER ID is required"
                    value = listOf(8142520, 8142521)
                }
            }

            queryParameter<Int>("messagesToFetch") {
                description = "Number of notices to fetch (1–100, default: 10)"

                example("Default") {
                    summary = "Default value"
                    description = "Fetch default number of notices"
                    value = 10
                }

                example("Maximum") {
                    summary = "Maximum value"
                    description = "Fetch the maximum allowed number of notices"
                    value = 100
                }
            }
        }

        response {
            OK to {
                description = "Notices retrieved successfully"

                body<List<Notice>> {
                    example("Message notice") {
                        value = listOf(
                            Notice(
                                id = Uuid.parse("733be787-0ad0-475a-98b7-00512caa9ccb"),
                                noticeType = NoticeType.NEW_MESSAGE,
                                contentType = "application/xml",
                                receiverHerId = 8142520,
                                senderHerId = 8142519,
                                businessDocumentId = "cc169595-bbf0-11dd-9ca9-117f241b4a68",
                                businessDocumentGenDate = Instant.parse("2008-11-26T19:31:17.281Z"),
                                isAppRec = false,
                                sourceSystem = "helsemelding EDI 2.0 edi-adapter, v1.0"
                            ),
                            Notice(
                                id = Uuid.parse("68e60a2b-5990-408c-b99b-089d8657d6ed"),
                                noticeType = NoticeType.REFUSED_MESSAGE,
                                contentType = "application/xml",
                                receiverHerId = 8142520,
                                senderHerId = 8142519,
                                businessDocumentId = "cc169595-bbf0-11dd-9ca9-117f241b4a68",
                                businessDocumentGenDate = Instant.parse("2008-11-26T19:31:17.281Z"),
                                isAppRec = false,
                                sourceSystem = "helsemelding EDI 2.0 edi-adapter, v1.0",
                                refusedReason = "Message rejected by receiver"
                            )
                        )
                    }
                }
            }

            BadRequest to {
                description =
                    "Bad request. Required query parameter `receiverHerIds` is missing."

                body<String> {
                    example("Missing receiverHerIds") {
                        summary = "receiverHerIds missing"
                        description =
                            "The mandatory query parameter `receiverHerIds` was not provided."
                        value = "Receiver her ids are missing"
                    }
                }
            }

            InternalServerError to {
                description = "Unexpected server error"
            }
        }
    }

    /* =============================================================
     * POST /mshConfiguration
     * ============================================================= */

    const val POST_MSH_CONFIGURATION = "/mshConfiguration"

    val postMshConfigurationDocs: RouteConfig.() -> Unit = {
        summary = "Update MSH configuration for given HerIds"
        description = """
            NB! ReceiveNotificationChannel = Kafka is only viable for NHN internal actors.
        """.trimIndent()

        request {
            body<PostMshConfigurationRequest> {
                required = true

                example("ApiPolling configuration") {
                    summary = "Configure API polling notification channel"
                    value = PostMshConfigurationRequest(
                        mshConfigurations = listOf(
                            MshConfiguration(
                                herId = 8142520,
                                receiveNotificationChannel = ReceiveNotificationChannel.API_POLLING,
                                receiveRefusedMessageNotices = false,
                                rejectMessageFilters = RejectMessageFilters(
                                    messageFunction = listOf("string"),
                                    xmlNamespace = listOf("string")
                                )
                            )
                        )
                    )
                }

                example("Kafka configuration (NHN internal only)") {
                    summary = "Configure Kafka notification channel"
                    description = "Only viable for NHN internal actors"
                    value = PostMshConfigurationRequest(
                        mshConfigurations = listOf(
                            MshConfiguration(
                                herId = 8142520,
                                receiveNotificationChannel = ReceiveNotificationChannel.KAFKA,
                                receiveRefusedMessageNotices = true
                            )
                        )
                    )
                }
            }
        }

        response {
            NoContent to {
                description = "MSH configuration updated successfully"
            }

            BadRequest to {
                description = "Invalid request payload"

                body<String> {
                    example("Bad request") {
                        value = "Invalid MSH configuration payload"
                    }
                }
            }

            InternalServerError to {
                description = "Internal server error"

                body<String> {
                    example("Internal error") {
                        value = "Internal server error"
                    }
                }
            }
        }
    }
}
