package no.nav.helsemelding.ediadapter.server.plugin

import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.helsemelding.ediadapter.server.config

fun Application.configureRoutes(
    ediClientV1: HttpClient,
    ediClientV2: HttpClient,
    ediClientV3: HttpClient,
    registry: PrometheusMeterRegistry
) {
    routing {
        swaggerRoutes()
        internalRoutes(registry)

        authenticate(config().azureAuth.issuer.value) {
            externalRoutes(ediClientV1, ediClientV2, ediClientV3)
        }
    }
}

fun Route.swaggerRoutes() {
    route("api.json") {
        openApi()
    }
    route("swagger") {
        swaggerUI("/api.json") {
        }
    }
}

fun Route.internalRoutes(registry: PrometheusMeterRegistry) {
    get("/prometheus") {
        call.respond(registry.scrape())
    }
    route("/internal") {
        get("/health/liveness") {
            call.respondText("I'm alive! :)")
        }
        get("/health/readiness") {
            call.respondText("I'm ready! :)")
        }
    }
}

fun Route.externalRoutes(ediClientV1: HttpClient, ediClientV2: HttpClient, ediClientV3: HttpClient) {
    route("/api/v1") {
        v1Routes(ediClientV1)
    }
    route("/api/v2") {
        v2Routes(ediClientV2)
    }
    route("/api/v3") {
        v3Routes(ediClientV3)
    }
}
