package no.nav.syfo.brukerinformasjon.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.kotest.koin.KoinExtension
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
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.mockedAuthModule
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import no.nav.syfo.utils.objectMapper
import org.amshove.kluent.shouldBeEqualTo
import org.koin.dsl.module
import org.koin.test.KoinTest

class BrukerinformasjonApiKtTest : FunSpec(), KoinTest {

    val arbeidsgiverService = mockkClass(ArbeidsgiverService::class)
    val mockedArbeidsgiverModule = module { single { ArbeidsgiverService(TODO(), TODO()) } }

    override fun extensions() =
        listOf(KoinExtension(listOf(mockedAuthModule, mockedArbeidsgiverModule)))

    init {
        beforeTest {
            clearMocks(arbeidsgiverService)
            coEvery { arbeidsgiverService.getArbeidsgivere(any(), any()) } returns
                listOf(
                    Arbeidsgiverinfo(
                        orgnummer = "orgnummer",
                        juridiskOrgnummer = "juridiskOrgnummer",
                        navn = "",
                        aktivtArbeidsforhold = true,
                        naermesteLeder = null,
                    ),
                )
        }

        context("Test brukerinformasjon-api med tilgangskontroll") {
            with(TestApplicationEngine()) {
                setUpTestApplication()
                setUpAuth()

                application.routing {
                    authenticate("tokenx") { route("/api/v2") { registrerBrukerinformasjonApi() } }
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
                            response.content!!,
                        ) shouldBeEqualTo
                            Brukerinformasjon(
                                arbeidsgivere =
                                    listOf(
                                        Arbeidsgiverinfo(
                                            orgnummer = "orgnummer",
                                            juridiskOrgnummer = "juridiskOrgnummer",
                                            navn = "",
                                            aktivtArbeidsforhold = true,
                                            naermesteLeder = null,
                                        ),
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
    }
}
