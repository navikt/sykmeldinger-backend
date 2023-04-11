package no.nav.syfo.sykmeldingstatus.api.v2

import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
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
import org.amshove.kluent.shouldBeEqualTo

class SykmeldingBekreftAvvistApiSpec : FunSpec({
    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)

    beforeTest {
        clearAllMocks()
        coEvery { sykmeldingStatusService.registrerBekreftetAvvist(any(), any(), any()) } just Runs
    }

    context("Test SykmeldingBekreftAvvistApi for sluttbruker med tilgangskontroll") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            setUpAuth()
            application.routing {
                authenticate("tokenx") {
                    route("/api/v2") {
                        registerSykmeldingBekreftAvvistApiV2(sykmeldingStatusService)
                    }
                }
            }

            test("Bruker skal få bekrefte sin egen avviste sykmelding") {
                val sykmeldingId = "123"
                with(
                    handleRequest(HttpMethod.Post, "/api/v2/sykmeldinger/$sykmeldingId/bekreftAvvist") {
                        addHeader(
                            "AUTHORIZATION",
                            "Bearer ${generateJWT(
                                "client",
                                "clientId",
                                subject = "12345678910",
                                issuer = "issuer",
                            )}",
                        )
                    },
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Accepted
                }
            }

            test("Bruker skal ikke få bekrefte sin egen sykmelding når den ikke kan bekreftes") {
                val sykmeldingId = "123"
                coEvery { sykmeldingStatusService.registrerBekreftetAvvist(any(), any(), any()) } throws InvalidSykmeldingStatusException("Invalid status")
                with(
                    handleRequest(HttpMethod.Post, "/api/v2/sykmeldinger/$sykmeldingId/bekreftAvvist") {
                        addHeader(
                            "AUTHORIZATION",
                            "Bearer ${generateJWT(
                                "client",
                                "clientId",
                                subject = "12345678910",
                                issuer = "issuer",
                            )}",
                        )
                    },
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                }
            }

            test("Skal ikke kunne bekrefte annen brukers sykmelding") {
                coEvery { sykmeldingStatusService.registrerBekreftetAvvist(any(), any(), any()) } throws SykmeldingStatusNotFoundException("Not Found", RuntimeException("Ingen tilgang"))
                with(
                    handleRequest(HttpMethod.Post, "/api/v2/sykmeldinger/123/bekreftAvvist") {
                        addHeader(
                            "Authorization",
                            "Bearer ${generateJWT(
                                "client",
                                "clientId",
                                subject = "00000000000",
                                issuer = "issuer",
                            )}",
                        )
                    },
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.NotFound
                }
            }

            test("Skal ikke kunne bruke apiet med token med feil audience") {
                with(
                    handleRequest(HttpMethod.Post, "/api/v2/sykmeldinger/123/bekreftAvvist") {
                        addHeader(
                            "Authorization",
                            "Bearer ${generateJWT(
                                "client",
                                "annenservice",
                                subject = "12345678910",
                                issuer = "issuer",
                            )}",
                        )
                    },
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
