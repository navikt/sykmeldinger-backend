package no.nav.syfo.sykmeldingstatus.api

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockkClass
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.application.setupAuth
import no.nav.syfo.client.SyfosmregisterClient
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.testutils.generateJWT
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingBekreftApiSpek : Spek({

    val syfosmregisterClient = mockkClass(SyfosmregisterClient::class)
    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)

    beforeEachTest {
        clearAllMocks()
        every { sykmeldingStatusService.registrerBekreftet(any(), any(), any(), any()) } just Runs
    }

    describe("Test SykmeldingBekreftAPI for sluttbruker med tilgangskontroll") {
        with(TestApplicationEngine()) {
            start(true)
            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }

            val env = Environment(jwtIssuer = "issuer",
                kafkaBootstrapServers = "",
                stsOidcIssuer = "https://security-token-service.nais.preprod.local",
                stsOidcAudience = "preprod.local")

            val mockJwkProvider = mockkClass(JwkProvider::class)
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()
            val vaultSecrets = VaultSecrets("", "", "1", "", "", "loginservice")

            application.setupAuth(vaultSecrets, jwkProvider, env.jwtIssuer, env, mockJwkProvider)
            application.routing { authenticate("jwt") { registerSykmeldingBekreftApi(syfosmregisterClient, sykmeldingStatusService) } }

            it("Bruker skal få bekrefte sin egen sykmelding med status APEN") {
                val sykmeldingId = "123"
                coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } returns SykmeldingStatusEventDTO(StatusEventDTO.APEN, OffsetDateTime.now(ZoneOffset.UTC))
                with(handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/$sykmeldingId/bekreft") {
                    addHeader("AUTHORIZATION", "Bearer ${generateJWT("client",
                        "loginservice",
                        subject = "12345678910",
                        issuer = env.jwtIssuer)}")
                }) {
                    response.status() shouldEqual HttpStatusCode.Accepted
                }
            }

            it("Bruker skal få bekrefte sin egen sykmelding med status BEKREFTET") {
                val sykmeldingId = "123"
                coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } returns SykmeldingStatusEventDTO(StatusEventDTO.BEKREFTET, OffsetDateTime.now(ZoneOffset.UTC))
                with(handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/$sykmeldingId/bekreft") {
                    addHeader("AUTHORIZATION", "Bearer ${generateJWT("client",
                        "loginservice",
                        subject = "12345678910",
                        issuer = env.jwtIssuer)}")
                }) {
                    response.status() shouldEqual HttpStatusCode.Accepted
                }
            }

            it("Bruker skal ikke få bekrefte sin egen sykmelding med status SENDT") {
                val sykmeldingId = "123"
                coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } returns SykmeldingStatusEventDTO(StatusEventDTO.SENDT, OffsetDateTime.now(ZoneOffset.UTC))
                with(handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/$sykmeldingId/bekreft") {
                    addHeader("AUTHORIZATION", "Bearer ${generateJWT("client",
                        "loginservice",
                        subject = "12345678910",
                        issuer = env.jwtIssuer)}")
                }) {
                    response.status() shouldEqual HttpStatusCode.BadRequest
                }
            }

            it("Bruker skal ikke få bekrefte sin egen sykmelding med status AVBRUTT") {
                val sykmeldingId = "123"
                coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } returns SykmeldingStatusEventDTO(StatusEventDTO.AVBRUTT, OffsetDateTime.now(ZoneOffset.UTC))
                with(handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/$sykmeldingId/bekreft") {
                    addHeader("AUTHORIZATION", "Bearer ${generateJWT("client",
                        "loginservice",
                        subject = "12345678910",
                        issuer = env.jwtIssuer)}")
                }) {
                    response.status() shouldEqual HttpStatusCode.BadRequest
                }
            }

            it("Skal ikke kunne bekrefte annen brukers sykmelding") {
                coEvery { syfosmregisterClient.hentSykmeldingstatus(any(), any()) } throws RuntimeException("Ikke tilgang")
                with(handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/123/bekreft") {
                    addHeader("Authorization", "Bearer ${generateJWT(
                        "client",
                        "loginservice",
                        subject = "00000000000",
                        issuer = env.jwtIssuer)}")
                }) {
                    response.status() shouldEqual HttpStatusCode.NotFound
                }
            }

            it("Skal ikke kunne bruke apiet med token med feil audience") {
                with(handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/123/bekreft") {
                    addHeader("Authorization", "Bearer ${generateJWT(
                        "client",
                        "annenservice",
                        subject = "12345678910",
                        issuer = env.jwtIssuer)}")
                }) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
