package no.nav.syfo.brukerinformasjon.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.auth.authenticate
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkClass
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.client.OidcToken
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.model.Navn
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class BrukerinformasjonApiKtTest : Spek({
    val pdlPersonService = mockkClass(PdlPersonService::class)
    val stsOidcClient = mockkClass(StsOidcClient::class)
    val arbeidsgiverService = mockkClass(ArbeidsgiverService::class)

    beforeEachTest {
        clearMocks(pdlPersonService)
        clearMocks(arbeidsgiverService)
        coEvery { stsOidcClient.oidcToken() } returns OidcToken("accesstoken", "type", 1L)
        coEvery { pdlPersonService.getPerson(any(), any(), any(), any()) } returns PdlPerson(Navn("Fornavn", null, "Etternavn"), "aktorId", false)
        coEvery { arbeidsgiverService.getArbeidsgivere(any(), any(), any()) } returns listOf(Arbeidsgiverinfo(orgnummer = "orgnummer", juridiskOrgnummer = "juridiskOrgnummer", navn = "", stillingsprosent = "50.0", stilling = "", aktivtArbeidsforhold = true, naermesteLeder = null))
    }

    describe("Test brukerinformasjon-api med tilgangskontroll") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()

            application.routing {
                authenticate("jwt") {
                    route("/api/v1") {
                        registrerBrukerinformasjonApi(arbeidsgiverService, pdlPersonService, stsOidcClient)
                    }
                }
            }

            it("Får hentet riktig informasjon for innlogget bruker uten diskresjonskode") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/brukerinformasjon") {
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
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    coVerify(exactly = 1) { arbeidsgiverService.getArbeidsgivere(any(), any(), any()) }
                    objectMapper.readValue<Brukerinformasjon>(response.content!!) shouldBeEqualTo Brukerinformasjon(
                        arbeidsgivere = listOf(Arbeidsgiverinfo(orgnummer = "orgnummer", juridiskOrgnummer = "juridiskOrgnummer", navn = "", stillingsprosent = "50.0", stilling = "", aktivtArbeidsforhold = true, naermesteLeder = null)),
                        strengtFortroligAdresse = false
                    )
                }
            }

            it("Får hentet riktig informasjon for innlogget bruker med diskresjonskode") {
                coEvery { pdlPersonService.getPerson(any(), any(), any(), any()) } returns PdlPerson(Navn("Fornavn", null, "Etternavn"), "aktorId", true)
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/brukerinformasjon") {
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
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    coVerify(exactly = 0) { arbeidsgiverService.getArbeidsgivere(any(), any(), any()) }
                    objectMapper.readValue<Brukerinformasjon>(response.content!!) shouldBeEqualTo Brukerinformasjon(
                        arbeidsgivere = emptyList(),
                        strengtFortroligAdresse = true
                    )
                }
            }

            it("Skal ikke kunne bruke apiet med token med feil audience") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/brukerinformasjon") {
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
