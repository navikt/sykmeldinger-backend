package no.nav.syfo.sykmeldingstatus.api.v1

import io.kotest.core.spec.style.FunSpec
import io.ktor.auth.authenticate
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.route
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
import org.amshove.kluent.shouldBeEqualTo

class SykmeldingAvbrytApiSpek : FunSpec({

    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)

    beforeTest {
        clearAllMocks()
        coEvery { sykmeldingStatusService.registrerStatus(any(), any(), any(), any(), any()) } just Runs
    }

    context("Test SykmeldingAvbrytAPI for sluttbruker med tilgangskontroll") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()

            application.routing {
                authenticate("jwt") {
                    route("/api/v1") {
                        registerSykmeldingAvbrytApi(sykmeldingStatusService)
                    }
                }
            }

            test("Bruker skal få avbryte sin egen sykmelding") {
                val sykmeldingId = "123"
                with(
                    handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/$sykmeldingId/avbryt") {
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        addHeader(
                            "AUTHORIZATION",
                            "Bearer ${generateJWT(
                                "client",
                                "loginserviceId2",
                                subject = "12345678910",
                                issuer = env.jwtIssuer
                            )}"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Accepted
                }
            }

            test("Bruker skal ikke få avbryte sin egen sykmelding når den ikke kan avbrytes") {
                val sykmeldingId = "123"
                coEvery { sykmeldingStatusService.registrerStatus(any(), any(), any(), any(), any()) } throws InvalidSykmeldingStatusException("Invalid status")
                with(
                    handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/$sykmeldingId/avbryt") {
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        addHeader(
                            "AUTHORIZATION",
                            "Bearer ${generateJWT(
                                "client",
                                "loginserviceId2",
                                subject = "12345678910",
                                issuer = env.jwtIssuer
                            )}"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                }
            }

            test("Skal ikke kunne avbryte annen brukers sykmelding") {
                coEvery { sykmeldingStatusService.registrerStatus(any(), any(), any(), any(), any()) } throws SykmeldingStatusNotFoundException("Not Found", RuntimeException("Ingen tilgang"))
                with(
                    handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/123/avbryt") {
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        addHeader(
                            "Authorization",
                            "Bearer ${generateJWT(
                                "client",
                                "loginserviceId2",
                                subject = "00000000000",
                                issuer = env.jwtIssuer
                            )}"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.NotFound
                }
            }

            test("Skal ikke kunne bruke apiet med token med feil audience") {
                with(
                    handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/123/avbryt") {
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        addHeader(
                            "Authorization",
                            "Bearer ${generateJWT(
                                "client",
                                "annenservice",
                                subject = "12345678910",
                                issuer = env.jwtIssuer
                            )}"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
