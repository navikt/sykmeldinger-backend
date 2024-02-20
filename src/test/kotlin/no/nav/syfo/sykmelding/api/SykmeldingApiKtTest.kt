package no.nav.syfo.sykmelding.api

import com.auth0.jwt.interfaces.Payload
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import no.nav.syfo.plugins.configureAuth
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.model.Sykmelding
import no.nav.syfo.sykmeldingstatus.getSykmeldingDTO
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

class SykmeldingApiKtTest : KoinTest {
    val sykmeldingService = mockkClass(SykmeldingService::class)
    val mockPayload = mockk<Payload>()

    @BeforeEach
    fun init() {
        clearAllMocks()
        coEvery { sykmeldingService.getSykmelding(any(), any()) } returns getSykmeldingDTO()
        coEvery { sykmeldingService.getSykmeldinger(any()) } returns listOf(getSykmeldingDTO())
        every { mockPayload.subject } returns "123"

        startKoin {
            modules(
                mockedAuthModule,
                module { single { sykmeldingService } },
            )
        }
    }

    @AfterEach fun cleanup() = stopKoin()

    @Test
    fun `Hent sykmeldinger`() = testApplication {
        this.setUpSykmeldingApi()

        val client = createTestHttpClient()
        val response = client.get("/api/v2/sykmeldinger") { headers { validAuthHeader() } }

        response.status shouldBeEqualTo HttpStatusCode.OK
        response.body<List<Sykmelding>>().size shouldBeEqualTo 1
    }

    @Test
    fun `Hent sykmeldinger med fom og tom`() = testApplication {
        setUpSykmeldingApi()

        val client = createTestHttpClient()
        val response =
            client.get("/api/v2/sykmeldinger?fom=2020-01-20&tom=2020-02-10") {
                headers { validAuthHeader() }
            }

        response.status shouldBeEqualTo HttpStatusCode.OK
        response.body<List<Sykmelding>>().size shouldBeEqualTo 1
    }

    @Test
    fun `Hent sykmeldinger med fom og tom og exclude`() = testApplication {
        setUpSykmeldingApi()

        val client = createTestHttpClient()
        val response =
            client.get("/api/v2/sykmeldinger?exclude=AVBRUTT&fom=2020-01-20&tom=2020-02-10") {
                headers { validAuthHeader() }
            }

        response.status shouldBeEqualTo HttpStatusCode.OK
        response.body<List<Sykmelding>>().size shouldBeEqualTo 1
    }

    @Test
    fun `Sykmelding by id OK`() = testApplication {
        setUpSykmeldingApi()

        val client = createTestHttpClient()
        val response =
            client.get("/api/v2/sykmeldinger/sykmeldingid") { headers { validAuthHeader() } }

        response.status shouldBeEqualTo HttpStatusCode.OK
    }

    @Test
    fun `Sykmeldinger OK`() = testApplication {
        setUpSykmeldingApi()

        val client = createTestHttpClient()
        val response = client.get("/api/v2/sykmeldinger") { headers { validAuthHeader() } }

        response.status shouldBeEqualTo HttpStatusCode.OK
    }

    @Test
    fun `Sykmeldinger OK, with cookie`() = testApplication {
        setUpSykmeldingApi()

        val client = createTestHttpClient()
        val response =
            client.get("/api/v2/sykmeldinger") {
                headers {
                    append(
                        HttpHeaders.Cookie,
                        "selvbetjening-idtoken=${
                            generateJWT(
                                "client",
                                "clientId",
                                subject = "12345678901",
                                issuer = "issuer",
                            )
                        }",
                    )
                }
            }
        response.status shouldBeEqualTo HttpStatusCode.OK
    }

    @Test
    fun `Unauthorized, incorrect audience`() = testApplication {
        setUpSykmeldingApi()

        val client = createTestHttpClient()
        val response =
            client.get("/api/v2/sykmeldinger") {
                headers {
                    append(
                        HttpHeaders.Authorization,
                        "Bearer ${
                            generateJWT(
                                "client",
                                "loginservice2",
                                subject = "12345678901",
                                issuer = "issuer",
                            )
                        }",
                    )
                }
            }

        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
    }

    @Test
    fun `Unauthorized, nivå 3`() = testApplication {
        setUpSykmeldingApi()

        val client = createTestHttpClient()
        val response =
            client.get("/api/v2/sykmeldinger") {
                headers {
                    append(
                        HttpHeaders.Authorization,
                        "Bearer ${
                            generateJWT(
                                "client",
                                "clientId",
                                subject = "12345678901",
                                issuer = "issuer",
                                level = "Level3",
                            )
                        }",
                    )
                }
            }

        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
    }

    @Test
    fun `Unauthorized, missing token`() = testApplication {
        setUpSykmeldingApi()

        val client = createTestHttpClient()
        val response = client.get("/api/v2/sykmeldinger")

        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
    }
}

private fun TestApplicationBuilder.setUpSykmeldingApi() {
    application {
        configureTestApplication()
        configureAuth()
    }

    routing { authenticate("tokenx") { route("/api/v2") { registerSykmeldingApiV2() } } }
}

private fun HeadersBuilder.validAuthHeader() {
    append(
        HttpHeaders.Authorization,
        "Bearer ${
            generateJWT(
                "client",
                "clientId",
                subject = "12345678901",
                issuer = "issuer",
            )
        }",
    )
}
