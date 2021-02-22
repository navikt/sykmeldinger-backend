package no.nav.syfo.testutils

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.server.testing.TestApplicationEngine
import io.mockk.mockkClass
import no.nav.syfo.Environment
import no.nav.syfo.application.setupAuth
import no.nav.syfo.log
import no.nav.syfo.sykmelding.exception.setUpSykmeldingExceptionHandler
import no.nav.syfo.sykmeldingstatus.exception.setUpSykmeldingStatusExeptionHandler
import java.nio.file.Paths

fun TestApplicationEngine.setUpTestApplication() {
    start(true)
    application.install(StatusPages) {
        exception<Throwable> { cause ->
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
            log.error("Caught exception", cause)
        }
        setUpSykmeldingStatusExeptionHandler()
        setUpSykmeldingExceptionHandler()
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
    val audience = listOf("loginserviceId1", "loginserviceId2")
    val env = Environment(
        jwtIssuer = "issuer",
        kafkaBootstrapServers = "",
        stsOidcIssuer = "https://security-token-service.nais.preprod.local",
        stsOidcAudience = "preprod.local",
        pdlGraphqlPath = "http://graphql",
        cluster = "dev-fss",
        loginserviceIdportenDiscoveryUrl = "url",
        loginserviceIdportenAudience = audience,
        truststorePassword = "",
        truststore = ""
    )

    val mockJwkProvider = mockkClass(JwkProvider::class)
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    val jwkProvider = JwkProviderBuilder(uri).build()

    application.setupAuth(audience, jwkProvider, env.jwtIssuer, env, mockJwkProvider)
    return env
}
