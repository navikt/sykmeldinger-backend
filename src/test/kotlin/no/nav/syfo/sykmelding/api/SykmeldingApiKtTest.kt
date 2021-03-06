package no.nav.syfo.sykmelding.api

import com.auth0.jwt.interfaces.Payload
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import no.nav.syfo.application.jedisObjectMapper
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.model.Sykmelding
import no.nav.syfo.sykmeldingstatus.getSykmeldingDTO
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
class SykmeldingApiKtTest : Spek({

    val sykmeldingService = mockkClass(SykmeldingService::class)
    val mockPayload = mockk<Payload>()

    beforeEachTest {
        clearAllMocks()
        coEvery { sykmeldingService.hentSykmelding(any(), any(), any()) } returns getSykmeldingDTO()
        coEvery { sykmeldingService.hentSykmeldinger(any(), any(), any()) } returns listOf(getSykmeldingDTO())
    }

    describe("Sykmelding Api test") {
        with(TestApplicationEngine()) {
            every { mockPayload.subject } returns "123"
            setUpTestApplication()
            val env = setUpAuth()
            application.routing { authenticate("jwt") { registerSykmeldingApi(sykmeldingService) } }
            it("Hent sykmeldinger") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                            generateJWT(
                                "client",
                                "loginserviceId1",
                                subject = "12345678901",
                                issuer = env.jwtIssuer
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    jedisObjectMapper.readValue<List<Sykmelding>>(response.content!!).size shouldBeEqualTo 1
                }
            }
            it("Hent sykmeldinger med fom og tom") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger?fom=2020-01-20&tom=2020-02-10") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                            generateJWT(
                                "client",
                                "loginserviceId1",
                                subject = "12345678901",
                                issuer = env.jwtIssuer
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    jedisObjectMapper.readValue<List<Sykmelding>>(response.content!!).size shouldBeEqualTo 1
                }
            }
            it("Hent sykmeldinger med fom og tom og exclude") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger?exclude=AVBRUTT&fom=2020-01-20&tom=2020-02-10") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                            generateJWT(
                                "client",
                                "loginserviceId1",
                                subject = "12345678901",
                                issuer = env.jwtIssuer
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    jedisObjectMapper.readValue<List<Sykmelding>>(response.content!!).size shouldBeEqualTo 1
                }
            }
        }
    }
    describe("Sykmelding API with Authorization") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            val env = setUpAuth()
            application.routing { authenticate("jwt") { registerSykmeldingApi(sykmeldingService) } }
            it("Sykmelding by id OK") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger/sykmeldingid") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                            generateJWT(
                                "client",
                                "loginserviceId1",
                                subject = "12345678901",
                                issuer = env.jwtIssuer
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }
            it("Sykmeldinger OK") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                            generateJWT(
                                "client",
                                "loginserviceId1",
                                subject = "12345678901",
                                issuer = env.jwtIssuer
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }
            it("Unauthorized, incorrect audience") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                            generateJWT(
                                "client",
                                "loginservice2",
                                subject = "12345678901",
                                issuer = env.jwtIssuer
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
            it("Unauthorized, nivå 3") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                            generateJWT(
                                "client",
                                "loginserviceId1",
                                subject = "12345678901",
                                issuer = env.jwtIssuer,
                                level = "Level3"
                            )
                            }"
                        )
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
            it("Unauthorized, missing token") {
                with(handleRequest(HttpMethod.Get, "/api/v1/sykmeldinger") {}) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
