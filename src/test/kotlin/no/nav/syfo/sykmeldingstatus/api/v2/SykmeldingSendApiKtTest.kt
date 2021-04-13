package no.nav.syfo.sykmeldingstatus.api.v2

import io.ktor.auth.authenticate
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
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
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingSendApiKtTest : Spek({
    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)

    beforeEachTest {
        clearAllMocks()
        coEvery { sykmeldingStatusService.registrerUserEvent(any(), any(), any(), any()) } returns Unit
    }

    describe("Test SykmeldingSendApi for sluttbruker med tilgangskontroll") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()

            application.routing {
                authenticate("jwt") {
                    registrerSykmeldingSendApiV2(
                        sykmeldingStatusService
                    )
                }
            }

            it("Bruker skal få sende sin egen sykmelding") {
                val sykmeldingId = "123"
                with(
                    handleRequest(HttpMethod.Post, "/api/v2/sykmeldinger/$sykmeldingId/send") {
                        setBody(objectMapper.writeValueAsString(opprettSykmeldingUserEvent()))
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        addHeader(
                            "AUTHORIZATION",
                            "Bearer ${
                            generateJWT(
                                "client",
                                "loginserviceId2",
                                subject = "12345678910",
                                issuer = env.jwtIssuer
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Accepted
                }
            }

            it("Får bad request ved empty body") {
                val sykmeldingId = "123"
                with(
                    handleRequest(HttpMethod.Post, "/api/v2/sykmeldinger/$sykmeldingId/send") {
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        addHeader(
                            "AUTHORIZATION",
                            "Bearer ${
                            generateJWT(
                                "client",
                                "loginserviceId2",
                                subject = "12345678910",
                                issuer = env.jwtIssuer
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                }
            }

            it("Får bad request ved validateringsfeil") {
                val sykmeldingId = "123"
                with(
                    handleRequest(HttpMethod.Post, "/api/v2/sykmeldinger/$sykmeldingId/send") {
                        setBody(objectMapper.writeValueAsString(opprettSykmeldingUserEvent().copy(erOpplysnigeneRiktige = SporsmalSvar(sporsmaltekst = "", svartekster = "", svar = JaEllerNei.NEI))))
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        addHeader(
                            "AUTHORIZATION",
                            "Bearer ${
                            generateJWT(
                                "client",
                                "loginserviceId2",
                                subject = "12345678910",
                                issuer = env.jwtIssuer
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                }
            }

            it("Skal ikke kunne sende annen brukers sykmelding") {
                coEvery {
                    sykmeldingStatusService.registrerUserEvent(
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                } throws SykmeldingStatusNotFoundException("Not Found", RuntimeException("Ingen tilgang"))
                with(
                    handleRequest(HttpMethod.Post, "/api/v2/sykmeldinger/123/send") {
                        setBody(objectMapper.writeValueAsString(opprettSykmeldingUserEvent()))
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        addHeader(
                            "Authorization",
                            "Bearer ${
                            generateJWT(
                                "client",
                                "loginserviceId2",
                                subject = "00000000000",
                                issuer = env.jwtIssuer
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.NotFound
                }
            }

            it("Skal ikke kunne bruke apiet med token med feil audience") {
                with(
                    handleRequest(HttpMethod.Post, "/api/v2/sykmeldinger/123/send") {
                        setBody(objectMapper.writeValueAsString(opprettSykmeldingUserEvent()))
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        addHeader(
                            "Authorization",
                            "Bearer ${
                            generateJWT(
                                "client",
                                "annenservice",
                                subject = "12345678910",
                                issuer = env.jwtIssuer
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
        }
    }
})

fun opprettSykmeldingUserEvent(): SykmeldingUserEvent {
    return SykmeldingUserEvent(
        erOpplysnigeneRiktige = SporsmalSvar(
            sporsmaltekst = "",
            svartekster = "",
            svar = JaEllerNei.JA,
        ),
        uriktigeOpplysninger = null,
        arbeidssituasjon = SporsmalSvar(
            sporsmaltekst = "",
            svartekster = "",
            svar = ArbeidssituasjonDTO.ANNET,
        ),
        arbeidsgiverOrgnummer = null,
        nyNarmesteLeder = null,
        harBruktEgenmelding = null,
        egenmeldingsperioder = null,
        harForsikring = null,
    )
}
