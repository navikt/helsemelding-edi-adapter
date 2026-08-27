package no.nav.helsemelding.ediadapter.model

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

object FlexibleInstantSerializer : KSerializer<Instant> {

    private val oslo = TimeZone.of("Europe/Oslo")

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInstant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant =
        decoder.decodeString().toInstant()

    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeString(value.toString())

    private fun String.toInstant(): Instant {
        return try {
            Instant.parse(this)
        } catch (_: IllegalArgumentException) {
            try {
                LocalDateTime.parse(this).toInstant(oslo)
            } catch (e: IllegalArgumentException) {
                throw SerializationException("Invalid date-time: '$this'", e)
            }
        }
    }
}
