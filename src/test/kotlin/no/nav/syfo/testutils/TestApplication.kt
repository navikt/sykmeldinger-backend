package no.nav.syfo.testutils

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import java.nio.file.Paths
import no.nav.syfo.plugins.AuthConfiguration
import no.nav.syfo.sykmelding.exception.setUpSykmeldingExceptionHandler
import no.nav.syfo.sykmeldingstatus.api.v2.setUpSykmeldingSendApiV2ExeptionHandler
import no.nav.syfo.sykmeldingstatus.exception.setUpSykmeldingStatusExeptionHandler
import no.nav.syfo.utils.Environment
import no.nav.syfo.utils.logger
import org.koin.dsl.module

fun Application.configureTestApplication() {
    val logger = logger()

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
            logger.error("Caught exception", cause)
        }
        setUpSykmeldingStatusExeptionHandler()
        setUpSykmeldingExceptionHandler()
        setUpSykmeldingSendApiV2ExeptionHandler()
    }

    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
}

private fun getTestEnvironment(): Environment =
    Environment(
        cluster = "dev-fss",
        tokenXWellKnownUrl = "https://tokenx",
        clientIdTokenX = "clientId",
        dbHost = "",
        dbPort = "",
        dbName = "",
        databasePassword = "",
        databaseUsername = "",
        eregUrl = "",
        aadAccessTokenUrl = "",
        clientId = "",
        clientSecret = "",
        aaregScope = "",
        aaregUrl = "",
    )

val mockedAuthModule = module {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    val jwkProvider = JwkProviderBuilder(uri).build()
    val testEnvironment = getTestEnvironment()

    single {
        AuthConfiguration(
            jwkProviderTokenX = jwkProvider,
            tokenXIssuer = "issuer",
            clientIdTokenX = testEnvironment.clientIdTokenX,
        )
    }
}

fun HeadersBuilder.validAuthHeader(subject: String = "12345678901") {
    append(
        HttpHeaders.Authorization,
        "Bearer ${
            generateJWT(
                "client",
                "clientId",
                subject = subject,
                issuer = "issuer",
            )
        }",
    )
}

fun HeadersBuilder.invalidAudienceAuthHeader() {
    append(
        HttpHeaders.Authorization,
        "Bearer ${
            generateJWT(
                "client",
                "feil-audience",
                subject = "12345678901",
                issuer = "issuer",
            )
        }",
    )
}
