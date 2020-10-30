package no.nav.syfo.sykmelding.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.auth.authenticate
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkClass
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmeldingstatus.getSykmeldingModel
import no.nav.syfo.sykmeldingstatus.redis.objectMapper
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
class SykmeldingApiKtTest : Spek({

    val sykmeldingService = mockkClass(SykmeldingService::class)

    beforeEachTest {
        clearAllMocks()
        coEvery { sykmeldingService.hentSykmeldinger(any(), any()) } returns listOf(getSykmeldingModel())
    }

    describe("Sykmelding Api test") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            application.routing { registerSykmeldingApi(sykmeldingService) }
            it("Hent sykmeldinger") {
                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                    addHeader("Authorization", "Bearer token")
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    objectMapper.readValue<List<SykmeldingDTO>>(response.content!!).size shouldEqual 1
                }
            }
            it("Hent sykmeldinger med fom og tom") {
                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger?fom=2020-01-20&tom=2020-02-10") {
                    addHeader("Authorization", "Bearer token")
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    objectMapper.readValue<List<SykmeldingDTO>>(response.content!!).size shouldEqual 1
                }
            }
            it("Hent sykmeldinger med fom og tom og exclude") {
                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger?exclude=AVBRUTT&fom=2020-01-20&tom=2020-02-10") {
                    addHeader("Authorization", "Bearer token")
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    objectMapper.readValue<List<SykmeldingDTO>>(response.content!!).size shouldEqual 1
                }
            }
        }
    }
    describe("Sykmelding API with Authorization") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing { authenticate("jwt") { registerSykmeldingApi(sykmeldingService) } }
            it("OK") {
                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                    addHeader("Authorization", "Bearer ${generateJWT("client",
                            "loginserviceId1",
                            subject = "12345678901",
                            issuer = env.jwtIssuer)}")
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                }
            }
            it("Unauthorized, incorrect audience") {
                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                    addHeader("Authorization", "Bearer ${generateJWT("client",
                            "loginservice2",
                            subject = "12345678901",
                            issuer = env.jwtIssuer)}")
                }) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }
            it("Unauthorized, missing token") {
                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {}) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
