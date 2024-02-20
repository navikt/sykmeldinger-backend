package no.nav.syfo.sykmelding

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.plugins.configureAuth
import no.nav.syfo.sykmelding.api.registerSykmeldingApiV2
import no.nav.syfo.sykmelding.db.SykmeldingDb
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmeldingstatus.getSykmeldingDTO
import no.nav.syfo.testutils.configureTestApplication
import no.nav.syfo.testutils.createTestHttpClient
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

class SykmeldingApiIntegrationTest : KoinTest {
    val sykmeldingDb = mockk<SykmeldingDb>()
    val sykmeldingService =
        SykmeldingService(
            sykmeldingDb,
        )

    @BeforeEach
    fun init() {
        startKoin {
            modules(
                mockedAuthModule,
                module { single { sykmeldingService } },
            )
        }
    }

    @AfterEach fun cleanup() = stopKoin()

    @Test
    fun `Should get list of sykmeldinger OK`() = testApplication {
        setUpRegistrerSykmeldingApi()

        val sykmelding = getSykmeldingDTO()
        coEvery { sykmeldingDb.getSykmeldinger(any()) } returns listOf(sykmelding)

        val response = getSykmeldinger()

        response.status shouldBeEqualTo HttpStatusCode.OK
        response.body<List<SykmeldingDTO>>() shouldBeEqualTo listOf(sykmelding)
    }

    @Test
    fun `should get empty list of sykmeldinger OK`() = testApplication {
        setUpRegistrerSykmeldingApi()

        coEvery { sykmeldingDb.getSykmeldinger(any()) } returns emptyList()

        val response = getSykmeldinger()

        response.status shouldBeEqualTo HttpStatusCode.OK
        response.body<List<SykmeldingDTO>>() shouldBeEqualTo emptyList()
    }
}

private suspend fun ApplicationTestBuilder.getSykmeldinger(): HttpResponse {
    val client = createTestHttpClient()

    return client.get("/api/v2/sykmeldinger") { headers { validAuthHeader() } }
}

private fun TestApplicationBuilder.setUpRegistrerSykmeldingApi() {
    application {
        configureTestApplication()
        configureAuth()
    }

    routing { authenticate("tokenx") { route("/api/v2") { registerSykmeldingApiV2() } } }
}
