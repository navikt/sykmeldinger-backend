package no.nav.syfo.brukerinformasjon.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkClass
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.plugins.configureAuth
import no.nav.syfo.testutils.configureTestApplication
import no.nav.syfo.testutils.createTestHttpClient
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.mockedAuthModule
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest

class BrukerinformasjonApiKtTest : KoinTest {
    val arbeidsgiverService = mockkClass(ArbeidsgiverService::class)

    @BeforeEach
    fun init() {
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

        startKoin {
            modules(
                mockedAuthModule,
                module { single { arbeidsgiverService } },
            )
        }
    }

    @AfterEach fun cleanup() = stopKoin()

    @Test
    fun `Får hentet riktig informasjon for innlogget bruker`() = testApplication {
        setUpBrukerinformasjonApi()

        val client = createTestHttpClient()
        val response =
            client.get("/api/v2/brukerinformasjon") {
                contentType(ContentType.Application.Json)
                headers {
                    append(
                        HttpHeaders.Authorization,
                        "Bearer ${
                        generateJWT(
                            "client",
                            "clientId",
                            subject = "12345678910",
                            issuer = "issuer",
                        )
                    }",
                    )
                }
            }

        coVerify(exactly = 1) { arbeidsgiverService.getArbeidsgivere(any(), any()) }

        response.status shouldBeEqualTo HttpStatusCode.OK
        response.body<Brukerinformasjon>() shouldBeEqualTo
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

    @Test
    fun `Skal ikke kunne bruke apiet med token med feil audience`() = testApplication {
        setUpBrukerinformasjonApi()
        val client = createTestHttpClient()
        val response =
            client.get("/api/v2/brukerinformasjon") {
                contentType(ContentType.Application.Json)
                headers {
                    append(
                        HttpHeaders.Authorization,
                        "Bearer ${
                        generateJWT(
                            "client",
                            "annenservice",
                            subject = "12345678910",
                            issuer = "issuer",
                        )
                    }",
                    )
                }
            }

        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
    }
}

fun TestApplicationBuilder.setUpBrukerinformasjonApi() {
    application {
        configureTestApplication()
        configureAuth()
    }

    routing { authenticate("tokenx") { route("/api/v2") { registrerBrukerinformasjonApi() } } }
}
