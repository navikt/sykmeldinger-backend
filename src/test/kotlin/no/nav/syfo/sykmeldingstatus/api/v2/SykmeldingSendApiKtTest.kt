package no.nav.syfo.sykmeldingstatus.api.v2

import io.kotest.core.spec.style.FunSpec
import io.ktor.auth.authenticate
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.route
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

class SykmeldingSendApiKtTest : FunSpec({
    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)

    beforeTest {
        clearAllMocks()
        coEvery { sykmeldingStatusService.registrerUserEvent(any(), any(), any(), any()) } returns Unit
    }

    context("Test SykmeldingSendApi for sluttbruker med tilgangskontroll") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()

            application.routing {
                authenticate("jwt") {
                    route("/api/v2") {
                        registrerSykmeldingSendApiV2(
                            sykmeldingStatusService
                        )
                    }
                }
            }

            test("Bruker skal få sende sin egen sykmelding") {
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

            test("Får bad request ved empty body") {
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

            test("Får bad request ved validateringsfeil") {
                val sykmeldingId = "123"
                with(
                    handleRequest(HttpMethod.Post, "/api/v2/sykmeldinger/$sykmeldingId/send") {
                        setBody(objectMapper.writeValueAsString(opprettSykmeldingUserEvent().copy(erOpplysningeneRiktige = SporsmalSvar(sporsmaltekst = "", svartekster = "", svar = JaEllerNei.NEI))))
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

            test("Skal ikke kunne sende annen brukers sykmelding") {
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

            test("Skal ikke kunne bruke apiet med token med feil audience") {
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
        erOpplysningeneRiktige = SporsmalSvar(
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
        riktigNarmesteLeder = null,
        harBruktEgenmelding = null,
        egenmeldingsperioder = null,
        harForsikring = null,
    )
}
