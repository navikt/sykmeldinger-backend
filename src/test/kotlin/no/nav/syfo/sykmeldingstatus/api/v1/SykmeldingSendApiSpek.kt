package no.nav.syfo.sykmeldingstatus.api.v1

import io.ktor.auth.authenticate
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.util.KtorExperimentalAPI
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockkClass
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
class SykmeldingSendApiSpek : Spek({

    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)
    val arbeidsgiverService = mockkClass(ArbeidsgiverService::class)

    beforeEachTest {
        clearAllMocks()
        coEvery { sykmeldingStatusService.registrerSendt(any(), any(), any(), any(), any(), any()) } just Runs
        coEvery { arbeidsgiverService.getArbeidsgivere(any(), any(), any(), any()) } returns listOf(
            Arbeidsgiverinfo("orgnummer", "juridiskOrgnummer", "Bedriften AS", "100", "", true, null)
        )
    }

    describe("Test SykmeldingSendAPI for sluttbruker med tilgangskontroll") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()

            application.routing {
                authenticate("jwt") {
                    registerSykmeldingSendApi(
                        sykmeldingStatusService,
                        arbeidsgiverService
                    )
                }
            }

            it("Bruker skal få sende sin egen sykmelding") {
                val sykmeldingId = "123"
                with(
                    handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/$sykmeldingId/send") {
                        setBody(objectMapper.writeValueAsString(opprettSykmeldingSendEventUserDTO()))
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

            it("Bruker skal ikke få sende sin egen sykmelding hvis bruker ikke har arbeidsforhold hos oppgitt orgnummer") {
                val sykmeldingId = "123"
                coEvery { arbeidsgiverService.getArbeidsgivere(any(), any(), any(), any()) } returns emptyList()
                with(
                    handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/$sykmeldingId/send") {
                        setBody(objectMapper.writeValueAsString(opprettSykmeldingSendEventUserDTO()))
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

            it("Bruker skal ikke få sende sin egen sykmelding når den ikke kan sendes") {
                val sykmeldingId = "123"
                coEvery {
                    sykmeldingStatusService.registrerSendt(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                    )
                } throws InvalidSykmeldingStatusException("Invalid status")
                with(
                    handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/$sykmeldingId/send") {
                        setBody(objectMapper.writeValueAsString(opprettSykmeldingSendEventUserDTO()))
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
                    sykmeldingStatusService.registrerSendt(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                    )
                } throws SykmeldingStatusNotFoundException("Not Found", RuntimeException("Ingen tilgang"))
                with(
                    handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/123/send") {
                        setBody(objectMapper.writeValueAsString(opprettSykmeldingSendEventUserDTO()))
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
                    handleRequest(HttpMethod.Post, "/api/v1/sykmeldinger/123/send") {
                        setBody(objectMapper.writeValueAsString(opprettSykmeldingSendEventUserDTO()))
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

    describe("Be om ny nærmeste leder") {
        it("Skal ha spørsmål med svar JA for nærmeste leder hvis bruker ba om ny NL for aktivt arbeidsforhold") {
            val sporsmalOgSvarListe = tilSporsmalOgSvarListe(
                SykmeldingSendEventUserDTO(
                    orgnummer = "orgnummer",
                    beOmNyNaermesteLeder = true,
                    sporsmalOgSvarListe = null
                ),
                aktivtArbeidsforhold = true
            )

            sporsmalOgSvarListe.size shouldBeEqualTo 2
            sporsmalOgSvarListe.find { it.shortName == ShortNameDTO.NY_NARMESTE_LEDER } shouldBeEqualTo SporsmalOgSvarDTO(
                tekst = "Skal finne ny nærmeste leder",
                shortName = ShortNameDTO.NY_NARMESTE_LEDER,
                svartype = SvartypeDTO.JA_NEI,
                svar = "JA"
            )
        }
        it("Skal ha spørsmål med svar NEI for nærmeste leder hvis arbeidsforhold ikke er aktivt") {
            val sporsmalOgSvarListe = tilSporsmalOgSvarListe(opprettSykmeldingSendEventUserDTO(), aktivtArbeidsforhold = false)

            sporsmalOgSvarListe.size shouldBeEqualTo 2
            sporsmalOgSvarListe.find { it.shortName == ShortNameDTO.NY_NARMESTE_LEDER } shouldBeEqualTo SporsmalOgSvarDTO(
                tekst = "Skal finne ny nærmeste leder",
                shortName = ShortNameDTO.NY_NARMESTE_LEDER,
                svartype = SvartypeDTO.JA_NEI,
                svar = "NEI"
            )
        }
        it("Skal ha spørsmål med svar NEI for nærmeste leder hvis arbeidsforhold ikke er aktivt selv om bruker ber om ny NL") {
            val sporsmalOgSvarListe = tilSporsmalOgSvarListe(
                SykmeldingSendEventUserDTO(
                    orgnummer = "orgnummer",
                    beOmNyNaermesteLeder = true,
                    sporsmalOgSvarListe = null
                ),
                aktivtArbeidsforhold = false
            )

            sporsmalOgSvarListe.size shouldBeEqualTo 2
            sporsmalOgSvarListe.find { it.shortName == ShortNameDTO.NY_NARMESTE_LEDER } shouldBeEqualTo SporsmalOgSvarDTO(
                tekst = "Skal finne ny nærmeste leder",
                shortName = ShortNameDTO.NY_NARMESTE_LEDER,
                svartype = SvartypeDTO.JA_NEI,
                svar = "NEI"
            )
        }
        it("Skal ikke ha spørsmål om nærmeste leder hvis arbeidsforhold er aktivt og bruker ikke ba om ny NL") {
            val sporsmalOgSvarListe = tilSporsmalOgSvarListe(opprettSykmeldingSendEventUserDTO(), aktivtArbeidsforhold = true)

            sporsmalOgSvarListe.size shouldBeEqualTo 1
            sporsmalOgSvarListe.find { it.shortName == ShortNameDTO.NY_NARMESTE_LEDER } shouldBeEqualTo null
        }
    }
})

fun opprettSykmeldingSendEventUserDTO(): SykmeldingSendEventUserDTO {
    return SykmeldingSendEventUserDTO(
        orgnummer = "orgnummer",
        beOmNyNaermesteLeder = false,
        sporsmalOgSvarListe = null
    )
}
