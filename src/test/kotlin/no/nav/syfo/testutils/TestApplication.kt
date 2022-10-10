package no.nav.syfo.testutils

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.testing.TestApplicationEngine
import no.nav.syfo.Environment
import no.nav.syfo.application.setupAuth
import no.nav.syfo.log
import no.nav.syfo.sykmelding.exception.setUpSykmeldingExceptionHandler
import no.nav.syfo.sykmeldingstatus.api.v2.setUpSykmeldingSendApiV2ExeptionHandler
import no.nav.syfo.sykmeldingstatus.exception.setUpSykmeldingStatusExeptionHandler
import java.nio.file.Paths

val testAudience = listOf("loginserviceId1", "loginserviceId2")

fun getTestEnvironment(audience: List<String> = testAudience): Environment =
    Environment(
        eregUrl = "https://ereg",
        aaregUrl = "https://aareg",
        pdlGraphqlPath = "http://graphql",
        cluster = "dev-fss",
        loginserviceIdportenDiscoveryUrl = "url",
        loginserviceIdportenAudience = audience,
        tokenXWellKnownUrl = "https://tokenx",
        clientIdTokenX = "clientId",
        tokenXPrivateJwk = getDefaultRSAKey(),
        pdlAudience = "pdl",
        aaregAudience = "aareg",
        allowedOrigin = emptyList(),
        smregisterUrl = "smregistergcp",
        smregisterAudience = "smregaudience",
        dbHost = "",
        dbPort = "",
        dbName = "",
        databasePassword = "",
        databaseUsername = "",
    )

fun TestApplicationEngine.setUpTestApplication() {
    start(true)
    application.install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
            log.error("Caught exception", cause)
        }
        setUpSykmeldingStatusExeptionHandler()
        setUpSykmeldingExceptionHandler()
        setUpSykmeldingSendApiV2ExeptionHandler()
    }
    application.install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
}

fun TestApplicationEngine.setUpAuth(): Environment {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    val jwkProvider = JwkProviderBuilder(uri).build()
    val testEnvironment = getTestEnvironment()

    application.setupAuth(testAudience, jwkProvider, "issuer", jwkProvider, "tokenxissuer", testEnvironment.clientIdTokenX)
    return testEnvironment
}
