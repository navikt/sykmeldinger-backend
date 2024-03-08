package no.nav.syfo.sykmeldingstatus.api.v2

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkClass
import no.nav.syfo.plugins.configureAuth
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.testutils.configureTestApplication
import no.nav.syfo.testutils.createTestHttpClient
import no.nav.syfo.testutils.invalidAudienceAuthHeader
import no.nav.syfo.testutils.mockedAuthModule
import no.nav.syfo.testutils.validAuthHeader
import no.nav.syfo.utils.objectMapper
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest

class SykmeldingSendApiKtTest : KoinTest {
    val sykmeldingId = "123"
    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)

    @BeforeEach
    fun init() {
        clearAllMocks()
        coEvery { sykmeldingStatusService.createStatus(any(), any(), any()) } returns Unit

        startKoin {
            modules(
                mockedAuthModule,
                module { single { sykmeldingStatusService } },
            )
        }
    }

    @AfterEach fun cleanup() = stopKoin()

    @Test
    fun `Bruker skal få sende sin egen sykmelding`() = testApplication {
        setupSendSykmeldingApi()

        val client = createTestHttpClient()
        val response =
            client.post("/api/v3/sykmeldinger/$sykmeldingId/send") {
                setBody(objectMapper.writeValueAsString(opprettSykmeldingUserEvent()))
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    validAuthHeader()
                }
            }

        response.status shouldBeEqualTo HttpStatusCode.Accepted
    }

    @Test
    fun `Får bad request ved empty body`() = testApplication {
        setupSendSykmeldingApi()

        val client = createTestHttpClient()
        val response =
            client.post("/api/v3/sykmeldinger/$sykmeldingId/send") {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    validAuthHeader()
                }
            }

        response.status shouldBeEqualTo HttpStatusCode.BadRequest
    }

    @Test
    fun `Får bad request ved valideringsfeil`() = testApplication {
        setupSendSykmeldingApi()

        val client = createTestHttpClient()
        val response =
            client.post("/api/v3/sykmeldinger/$sykmeldingId/send") {
                setBody(
                    objectMapper.writeValueAsString(
                        opprettSykmeldingUserEvent()
                            .copy(
                                erOpplysningeneRiktige =
                                    SporsmalSvar(
                                        sporsmaltekst = "",
                                        svar = JaEllerNei.NEI,
                                    ),
                            ),
                    ),
                )
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    validAuthHeader()
                }
            }

        response.status shouldBeEqualTo HttpStatusCode.BadRequest
    }

    @Test
    fun `Skal ikke kunne sende annen brukers sykmelding`() = testApplication {
        setupSendSykmeldingApi()
        coEvery {
            sykmeldingStatusService.createStatus(
                any(),
                any(),
                any(),
            )
        } throws
            SykmeldingStatusNotFoundException(
                "Not Found",
                RuntimeException("Ingen tilgang"),
            )

        val client = createTestHttpClient()
        val response =
            client.post("/api/v3/sykmeldinger/123/send") {
                setBody(objectMapper.writeValueAsString(opprettSykmeldingUserEvent()))
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    validAuthHeader("00000000000")
                }
            }
        response.status shouldBeEqualTo HttpStatusCode.NotFound
    }

    @Test
    fun `Skal ikke kunne bruke apiet med token med feil audience`() = testApplication {
        setupSendSykmeldingApi()

        val client = createTestHttpClient()
        val response =
            client.post("/api/v3/sykmeldinger/123/send") {
                setBody(objectMapper.writeValueAsString(opprettSykmeldingUserEvent()))
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    invalidAudienceAuthHeader()
                }
            }

        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
    }
}

fun opprettSykmeldingUserEvent(): SykmeldingFormResponse {
    return SykmeldingFormResponse(
        erOpplysningeneRiktige =
            SporsmalSvar(
                sporsmaltekst = "",
                svar = JaEllerNei.JA,
            ),
        uriktigeOpplysninger = null,
        arbeidssituasjon =
            SporsmalSvar(
                sporsmaltekst = "",
                svar = Arbeidssituasjon.ANNET,
            ),
        arbeidsgiverOrgnummer = null,
        riktigNarmesteLeder = null,
        harBruktEgenmelding = null,
        egenmeldingsperioder = null,
        harForsikring = null,
        harBruktEgenmeldingsdager = null,
        egenmeldingsdager = null,
        fisker = null,
    )
}

private fun TestApplicationBuilder.setupSendSykmeldingApi() {
    application {
        configureTestApplication()
        configureAuth()
    }

    routing { authenticate("tokenx") { route("/api/v3") { registrerSykmeldingSendApiV3() } } }
}
