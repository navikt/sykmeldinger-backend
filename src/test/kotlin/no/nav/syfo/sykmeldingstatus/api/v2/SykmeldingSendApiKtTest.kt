package no.nav.syfo.sykmeldingstatus.api.v2

import io.kotest.core.spec.style.FunSpec
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkClass
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo

class SykmeldingSendApiKtTest :
    FunSpec({
        val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)

        beforeTest {
            clearAllMocks()
            coEvery { sykmeldingStatusService.createSendtStatus(any(), any(), any()) } returns Unit
        }

        context("Test SykmeldingSendApi for sluttbruker med tilgangskontroll") {
            with(TestApplicationEngine()) {
                setUpTestApplication()
                setUpAuth()

                application.routing {
                    authenticate("tokenx") {
                        route("/api/v3") {
                            registrerSykmeldingSendApiV3(
                                sykmeldingStatusService,
                            )
                        }
                    }
                }

                test("Bruker skal få sende sin egen sykmelding") {
                    val sykmeldingId = "123"
                    with(
                        handleRequest(HttpMethod.Post, "/api/v3/sykmeldinger/$sykmeldingId/send") {
                            setBody(objectMapper.writeValueAsString(opprettSykmeldingUserEvent()))
                            addHeader("Content-Type", ContentType.Application.Json.toString())
                            addHeader(
                                "AUTHORIZATION",
                                "Bearer ${
                                generateJWT(
                                    "client",
                                    "clientId",
                                    subject = "12345678910",
                                    issuer = "issuer",
                                )
                            }",
                            )
                        },
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Accepted
                    }
                }

                test("Får bad request ved empty body") {
                    val sykmeldingId = "123"
                    with(
                        handleRequest(HttpMethod.Post, "/api/v3/sykmeldinger/$sykmeldingId/send") {
                            addHeader("Content-Type", ContentType.Application.Json.toString())
                            addHeader(
                                "AUTHORIZATION",
                                "Bearer ${
                                generateJWT(
                                    "client",
                                    "clientId",
                                    subject = "12345678910",
                                    issuer = "issuer",
                                )
                            }",
                            )
                        },
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }

                test("Får bad request ved validateringsfeil") {
                    val sykmeldingId = "123"
                    with(
                        handleRequest(HttpMethod.Post, "/api/v3/sykmeldinger/$sykmeldingId/send") {
                            setBody(
                                objectMapper.writeValueAsString(
                                    opprettSykmeldingUserEvent()
                                        .copy(
                                            erOpplysningeneRiktige =
                                                SporsmalSvar(
                                                    sporsmaltekst = "",
                                                    svar = JaEllerNei.NEI
                                                )
                                        )
                                )
                            )
                            addHeader("Content-Type", ContentType.Application.Json.toString())
                            addHeader(
                                "AUTHORIZATION",
                                "Bearer ${
                                generateJWT(
                                    "client",
                                    "clientId",
                                    subject = "12345678910",
                                    issuer = "issuer",
                                )
                            }",
                            )
                        },
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }

                test("Skal ikke kunne sende annen brukers sykmelding") {
                    coEvery {
                        sykmeldingStatusService.createSendtStatus(
                            any(),
                            any(),
                            any(),
                        )
                    } throws
                        SykmeldingStatusNotFoundException(
                            "Not Found",
                            RuntimeException("Ingen tilgang")
                        )
                    with(
                        handleRequest(HttpMethod.Post, "/api/v3/sykmeldinger/123/send") {
                            setBody(objectMapper.writeValueAsString(opprettSykmeldingUserEvent()))
                            addHeader("Content-Type", ContentType.Application.Json.toString())
                            addHeader(
                                "Authorization",
                                "Bearer ${
                                generateJWT(
                                    "client",
                                    "clientId",
                                    subject = "00000000000",
                                    issuer = "issuer",
                                )
                            }",
                            )
                        },
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.NotFound
                    }
                }

                test("Skal ikke kunne bruke apiet med token med feil audience") {
                    with(
                        handleRequest(HttpMethod.Post, "/api/v3/sykmeldinger/123/send") {
                            setBody(objectMapper.writeValueAsString(opprettSykmeldingUserEvent()))
                            addHeader("Content-Type", ContentType.Application.Json.toString())
                            addHeader(
                                "Authorization",
                                "Bearer ${
                                generateJWT(
                                    "client",
                                    "annenservice",
                                    subject = "12345678910",
                                    issuer = "issuer",
                                )
                            }",
                            )
                        },
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }
            }
        }
    })

fun opprettSykmeldingUserEvent(): SykmeldingUserEvent {
    return SykmeldingUserEvent(
        erOpplysningeneRiktige =
            SporsmalSvar(
                sporsmaltekst = "",
                svar = JaEllerNei.JA,
            ),
        uriktigeOpplysninger = null,
        arbeidssituasjon =
            SporsmalSvar(
                sporsmaltekst = "",
                svar = ArbeidssituasjonDTO.ANNET,
            ),
        arbeidsgiverOrgnummer = null,
        riktigNarmesteLeder = null,
        harBruktEgenmelding = null,
        egenmeldingsperioder = null,
        harForsikring = null,
        harBruktEgenmeldingsdager = null,
        egenmeldingsdager = null,
    )
}
