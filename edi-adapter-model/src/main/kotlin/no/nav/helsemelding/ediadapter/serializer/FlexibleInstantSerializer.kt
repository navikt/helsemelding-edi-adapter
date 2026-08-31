package no.nav.helsemelding.ediadapter.serializer

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.handleErrorWith
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

internal object FlexibleInstantSerializer : KSerializer<Instant> {

    private val oslo = TimeZone.of("Europe/Oslo")

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInstant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant =
        decoder.decodeString().toInstant()

    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(value.toString())

    private fun String.toInstant(): Instant =
        Either
            .catch { Instant.parse(this) }
            .handleErrorWith {
                Either.catch {
                    LocalDateTime.parse(this)
                        .toInstant(oslo)
                }
            }
            .getOrElse { cause ->
                throw SerializationException(
                    "Invalid date-time: '$this'",
                    cause
                )
            }
}
