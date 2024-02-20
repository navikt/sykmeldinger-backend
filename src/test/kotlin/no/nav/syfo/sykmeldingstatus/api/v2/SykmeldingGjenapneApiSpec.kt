package no.nav.syfo.sykmeldingstatus.api.v2

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.testing.*
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockkClass
import no.nav.syfo.plugins.configureAuth
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.testutils.configureTestApplication
import no.nav.syfo.testutils.createTestHttpClient
import no.nav.syfo.testutils.invalidAudienceAuthHeader
import no.nav.syfo.testutils.mockedAuthModule
import no.nav.syfo.testutils.validAuthHeader
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest

class SykmeldingGjenapneApiSpec : KoinTest {
    val sykmeldingId = "123"
    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)

    @BeforeEach
    fun init() {
        clearAllMocks()
        coEvery { sykmeldingStatusService.createGjenapneStatus(any(), any()) } just Runs

        startKoin {
            modules(
                mockedAuthModule,
                module { single { sykmeldingStatusService } },
            )
        }
    }

    @AfterEach fun cleanup() = stopKoin()

    @Test
    fun `Bruker skal få gjenåpne sin egen sykmelding`() = testApplication {
        setupGjenapneSykmeldingApi()

        val client = createTestHttpClient()
        val response =
            client.post("/api/v2/sykmeldinger/$sykmeldingId/gjenapne") {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    validAuthHeader()
                }
            }

        response.status shouldBeEqualTo HttpStatusCode.Accepted
    }

    @Test
    fun `Bruker skal ikke få gjenåpne sin egen sykmelding når den ikke kan gjenåpnes`() =
        testApplication {
            setupGjenapneSykmeldingApi()
            coEvery { sykmeldingStatusService.createGjenapneStatus(any(), any()) } throws
                InvalidSykmeldingStatusException("Invalid status")

            val client = createTestHttpClient()
            val response =
                client.post("/api/v2/sykmeldinger/$sykmeldingId/gjenapne") {
                    headers {
                        append(HttpHeaders.ContentType, ContentType.Application.Json)
                        validAuthHeader()
                    }
                }

            response.status shouldBeEqualTo HttpStatusCode.BadRequest
        }

    @Test
    fun `Skal ikke kunne gjenåpne annen brukers sykmelding`() = testApplication {
        setupGjenapneSykmeldingApi()
        coEvery { sykmeldingStatusService.createGjenapneStatus(any(), any()) } throws
            SykmeldingStatusNotFoundException(
                "Not Found",
                RuntimeException("Ingen tilgang"),
            )

        val client = createTestHttpClient()
        val response =
            client.post("/api/v2/sykmeldinger/123/gjenapne") {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    validAuthHeader()
                }
            }

        response.status shouldBeEqualTo HttpStatusCode.NotFound
    }

    @Test
    fun `Skal ikke kunne bruke apiet med token med feil audience`() = testApplication {
        setupGjenapneSykmeldingApi()

        val client = createTestHttpClient()
        val response =
            client.post("/api/v2/sykmeldinger/123/gjenapne") {
                headers {
                    append(HttpHeaders.ContentType, ContentType.Application.Json)
                    invalidAudienceAuthHeader()
                }
            }

        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
    }
}

private fun TestApplicationBuilder.setupGjenapneSykmeldingApi() {
    application {
        configureTestApplication()
        configureAuth()
    }

    routing { authenticate("tokenx") { route("/api/v2") { registerSykmeldingGjenapneApiV2() } } }
}
