package no.nav.syfo.sykmeldingstatus.api

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
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.setUpAuth
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
            val env = setUpAuth()

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