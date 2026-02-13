package no.nav.helsemelding.ediadapter.server.util

import io.opentelemetry.api.trace.Span
import org.slf4j.Logger
import org.slf4j.MDC

class ExtendedLogger(private val logger: Logger) {

    fun debug(msg: String) {
        val ctx = Span.current().spanContext
        MDC.putCloseable("trace_id", ctx.traceId).use {
            logger.debug(msg)
        }
    }

    fun info(msg: String) {
        val ctx = Span.current().spanContext
        MDC.putCloseable("trace_id", ctx.traceId).use {
            logger.info(msg)
        }
    }

    fun warn(msg: String) {
        val ctx = Span.current().spanContext
        MDC.putCloseable("trace_id", ctx.traceId).use {
            logger.warn(msg)
        }
    }

    fun error(msg: String) {
        val ctx = Span.current().spanContext
        MDC.putCloseable("trace_id", ctx.traceId).use {
            logger.error(msg)
        }
    }
}
