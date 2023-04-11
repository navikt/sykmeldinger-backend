package no.nav.syfo.sykmelding.api

import com.auth0.jwt.interfaces.Payload
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.model.Sykmelding
import no.nav.syfo.sykmeldingstatus.getSykmeldingDTO
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.setUpAuth
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo

class SykmeldingApiKtTest : FunSpec({

    val sykmeldingService = mockkClass(SykmeldingService::class)
    val mockPayload = mockk<Payload>()

    beforeTest {
        clearAllMocks()
        coEvery { sykmeldingService.hentSykmelding(any(), any()) } returns getSykmeldingDTO()
        coEvery { sykmeldingService.hentSykmeldinger(any()) } returns listOf(getSykmeldingDTO())
    }

    context("Sykmelding Api test") {
        with(TestApplicationEngine()) {
            every { mockPayload.subject } returns "123"
            setUpTestApplication()
            setUpAuth()
            application.routing {
                authenticate("tokenx") {
                    route("/api/v2") {
                        registerSykmeldingApiV2(sykmeldingService)
                    }
                }
            }
            test("Hent sykmeldinger") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v2/sykmeldinger") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                                generateJWT(
                                    "client",
                                    "clientId",
                                    subject = "12345678901",
                                    issuer = "issuer",
                                )
                            }",
                        )
                    },
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    objectMapper.readValue<List<Sykmelding>>(response.content!!).size shouldBeEqualTo 1
                }
            }
            test("Hent sykmeldinger med fom og tom") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v2/sykmeldinger?fom=2020-01-20&tom=2020-02-10") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                                generateJWT(
                                    "client",
                                    "clientId",
                                    subject = "12345678901",
                                    issuer = "issuer",
                                )
                            }",
                        )
                    },
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    objectMapper.readValue<List<Sykmelding>>(response.content!!).size shouldBeEqualTo 1
                }
            }
            test("Hent sykmeldinger med fom og tom og exclude") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v2/sykmeldinger?exclude=AVBRUTT&fom=2020-01-20&tom=2020-02-10") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                                generateJWT(
                                    "client",
                                    "clientId",
                                    subject = "12345678901",
                                    issuer = "issuer",
                                )
                            }",
                        )
                    },
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    objectMapper.readValue<List<Sykmelding>>(response.content!!).size shouldBeEqualTo 1
                }
            }
        }
    }
    context("Sykmelding API with Authorization") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            setUpAuth()
            application.routing {
                authenticate("tokenx") {
                    route("/api/v2") {
                        registerSykmeldingApiV2(sykmeldingService)
                    }
                }
            }
            test("Sykmelding by id OK") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v2/sykmeldinger/sykmeldingid") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                                generateJWT(
                                    "client",
                                    "clientId",
                                    subject = "12345678901",
                                    issuer = "issuer",
                                )
                            }",
                        )
                    },
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }
            test("Sykmeldinger OK") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v2/sykmeldinger") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                                generateJWT(
                                    "client",
                                    "clientId",
                                    subject = "12345678901",
                                    issuer = "issuer",
                                )
                            }",
                        )
                    },
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }
            test("Sykmeldinger OK, with cookie") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v2/sykmeldinger") {
                        addHeader(
                            "Cookie",
                            "selvbetjening-idtoken=${
                                generateJWT(
                                    "client",
                                    "clientId",
                                    subject = "12345678901",
                                    issuer = "issuer",
                                )
                            }",
                        )
                    },
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                }
            }
            test("Unauthorized, incorrect audience") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v2/sykmeldinger") {
                        addHeader(
                            "Authorization",
                            "Bearer ${
                                generateJWT(
                                    "client",
                                    "loginservice2",
                                    subject = "12345678901",
                                    issuer = "issuer",
                                )
                            }",
                        )
                    },
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
            test("Unauthorized, nivå 3") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v2/sykmeldinger") {
                        addHeader(
                            "Authorization",
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
                    },
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
            test("Unauthorized, missing token") {
                with(handleRequest(HttpMethod.Get, "/api/v2/sykmeldinger") {}) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
