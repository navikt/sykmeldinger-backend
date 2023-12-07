package no.nav.syfo.brukerinformasjon.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkClass
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.objectMapper
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo

class BrukerinformasjonApiKtTest :
    FunSpec({
        val arbeidsgiverService = mockkClass(ArbeidsgiverService::class)

        beforeTest {
            clearMocks(arbeidsgiverService)
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns
                listOf(
                    Arbeidsgiverinfo(
                        orgnummer = "orgnummer",
                        juridiskOrgnummer = "juridiskOrgnummer",
                        navn = "",
                        stillingsprosent = "50.0",
                        stilling = "",
                        aktivtArbeidsforhold = true,
                        naermesteLeder = null
                    )
                )
        }

        context("Test brukerinformasjon-api med tilgangskontroll") {
            with(TestApplicationEngine()) {
                setUpTestApplication()
                setUpAuth()

                application.routing {
                    authenticate("tokenx") {
                        route("/api/v2") { registrerBrukerinformasjonApi(arbeidsgiverService) }
                    }
                }

                test("Får hentet riktig informasjon for innlogget bruker") {
                    with(
                        handleRequest(HttpMethod.Get, "/api/v2/brukerinformasjon") {
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
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                        coVerify(exactly = 1) { arbeidsgiverService.getArbeidsgivere(any(), any()) }
                        objectMapper.readValue<Brukerinformasjon>(
                            response.content!!
                        ) shouldBeEqualTo
                            Brukerinformasjon(
                                arbeidsgivere =
                                    listOf(
                                        Arbeidsgiverinfo(
                                            orgnummer = "orgnummer",
                                            juridiskOrgnummer = "juridiskOrgnummer",
                                            navn = "",
                                            stillingsprosent = "50.0",
                                            stilling = "",
                                            aktivtArbeidsforhold = true,
                                            naermesteLeder = null
                                        )
                                    ),
                            )
                    }
                }

                test("Skal ikke kunne bruke apiet med token med feil audience") {
                    with(
                        handleRequest(HttpMethod.Get, "/api/v2/brukerinformasjon") {
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
