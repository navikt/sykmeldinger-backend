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
import java.nio.file.Paths
import no.nav.syfo.plugins.AuthConfiguration
import no.nav.syfo.plugins.configureAuth
import no.nav.syfo.sykmelding.exception.setUpSykmeldingExceptionHandler
import no.nav.syfo.sykmeldingstatus.api.v2.setUpSykmeldingSendApiV2ExeptionHandler
import no.nav.syfo.sykmeldingstatus.exception.setUpSykmeldingStatusExeptionHandler
import no.nav.syfo.utils.Environment
import no.nav.syfo.utils.logger
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun getTestEnvironment(): Environment =
    Environment(
        cluster = "dev-fss",
        tokenXWellKnownUrl = "https://tokenx",
        clientIdTokenX = "clientId",
        dbHost = "",
        dbPort = "",
        dbName = "",
        databasePassword = "",
        databaseUsername = "",
    )

fun TestApplicationEngine.setUpTestApplication() {
    val logger = logger()

    start(true)
    application.install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
            logger.error("Caught exception", cause)
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

    // TODO: Figure out how to use koin modules in tests
    startKoin {
        modules(
            module {
                single {
                    AuthConfiguration(
                        jwkProviderTokenX = jwkProvider,
                        tokenXIssuer = "issuer",
                        clientIdTokenX = testEnvironment.clientIdTokenX,
                    )
                }
            },
        )
    }

    application.configureAuth()
    return testEnvironment
}

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
