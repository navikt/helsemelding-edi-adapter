package no.nav.helsemelding.ediadapter.server.plugin

import com.nimbusds.jwt.SignedJWT
import io.kotest.core.spec.style.StringSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.TestApplicationBuilder
import no.nav.helsemelding.ediadapter.server.auth.AuthConfig.Companion.getTokenSupportConfig
import no.nav.helsemelding.ediadapter.server.config
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.v3.tokenValidationSupport
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

internal const val INVALID_AUDIENCE = "api://dev-fss.helsemelding.some-other-service/.default"

internal fun StringSpec.routeTokenProvider(): (String) -> SignedJWT {
    val mockOAuth2Server = MockOAuth2Server()

    beforeSpec {
        mockOAuth2Server.start(port = 3344)
    }
    afterSpec {
        mockOAuth2Server.shutdown()
    }

    return { audience ->
        mockOAuth2Server.issueToken(
            issuerId = config().azureAuth.issuer.value,
            audience = audience,
            subject = "testUser"
        )
    }
}

internal fun ApplicationTestBuilder.createJsonEnabledClient(): HttpClient =
    createClient {
        install(ClientContentNegotiation) {
            json()
        }
    }

internal fun TestApplicationBuilder.installExternalRoutes(
    ediClientV1: HttpClient = fakeEdiClient { error("Should not be called") },
    ediClientV2: HttpClient = fakeEdiClient { error("Should not be called") },
    useAuthentication: Boolean = false,
    ediClientV3: HttpClient = fakeEdiClient { error("Should not be called") }
) {
    install(ContentNegotiation) {
        json()
    }

    val issuer = config().azureAuth.issuer.value

    if (useAuthentication) {
        install(Authentication) {
            tokenValidationSupport(
                issuer,
                getTokenSupportConfig()
            )
        }
    }

    routing {
        val externalRoutes: Route.() -> Unit = {
            externalRoutes(ediClientV1, ediClientV2, ediClientV3)
        }

        if (useAuthentication) {
            authenticate(issuer, build = externalRoutes)
        } else {
            externalRoutes(this)
        }
    }
}

internal fun fakeEdiClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
): HttpClient =
    HttpClient(MockEngine) {
        engine {
            addHandler(handler)
        }

        install(ClientContentNegotiation) {
            json()
        }
    }

internal suspend fun HttpClient.getWithAuth(
    url: String,
    getToken: (String) -> SignedJWT,
    audience: String = config().azureAuth.appScope.value
): HttpResponse =
    get(url) {
        header(
            Authorization,
            "Bearer ${getToken(audience).serialize()}"
        )
    }

internal suspend fun HttpClient.postWithAuth(
    url: String,
    getToken: (String) -> SignedJWT,
    audience: String = config().azureAuth.appScope.value,
    block: HttpRequestBuilder.() -> Unit = {}
): HttpResponse =
    post(url) {
        header(
            Authorization,
            "Bearer ${getToken(audience).serialize()}"
        )
        block()
    }

internal suspend fun withStreamingServer(
    ediClientV3: HttpClient,
    block: suspend HttpClient.(baseUrl: String) -> Unit
) {
    val server = embeddedServer(Netty, port = 0) {
        configureContentNegotiation()
        routing { externalRoutes(ediClientV3, ediClientV3, ediClientV3) }
    }
    try {
        server.start()
        val port = server.engine.resolvedConnectors().single().port
        HttpClient(CIO).use { client ->
            client.block("http://localhost:$port")
        }
    } finally {
        server.stop(0, 1000)
    }
}
