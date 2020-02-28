package no.nav.syfo.sykmeldingstatus.api

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.auth.authenticate
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockkClass
import java.nio.file.Paths
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.application.setupAuth
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingBekreftApiSpek : Spek({

    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)

    beforeEachTest {
        clearAllMocks()
        coEvery { sykmeldingStatusService.registrerBekreftet(any(), any(), any(), any(), any()) } just Runs
    }

    describe("Test SykmeldingBekreftAPI for sluttbruker med tilgangskontroll") {
        with(TestApplicationEngine()) {
            setUpTestApplication()

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
            application.routing { authenticate("jwt") { registerSykmeldingBekreftApi(sykmeldingStatusService) } }

            it("Bruker skal få bekrefte sin egen sykmelding") {
                val sykmeldingId = "123"
                with(handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/$sykmeldingId/bekreft") {
                    addHeader("AUTHORIZATION", "Bearer ${generateJWT("client",
                            "loginservice",
                            subject = "12345678910",
                            issuer = env.jwtIssuer)}")
                }) {
                    response.status() shouldEqual HttpStatusCode.Accepted
                }
            }

            it("Bruker skal ikke få bekrefte sin egen sykmelding når den ikke kan bekreftes") {
                val sykmeldingId = "123"
                coEvery { sykmeldingStatusService.registrerBekreftet(any(), any(), any(), any(), any()) } throws InvalidSykmeldingStatusException("Invalid status")

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
                coEvery { sykmeldingStatusService.registrerBekreftet(any(), any(), any(), any(), any()) } throws SykmeldingStatusNotFoundException("Not Found", RuntimeException("Ingen tilgang"))
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
