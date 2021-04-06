package no.nav.syfo.sykmeldingstatus.api.v1

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import io.ktor.auth.authenticate
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkClass
import no.nav.syfo.application.setupAuth
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.getTestEnvironment
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SykmeldingStatusSyfoServiceApiSpek : Spek({

    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)

    beforeEachTest {
        clearAllMocks()
    }

    describe("Test SykmeldingStatusAPI") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            application.routing { registerSykmeldingStatusSyfoServiceApi(sykmeldingStatusService) }

            it("Should successfully post Status") {
                val sykmeldingId = "123"
                coEvery { sykmeldingStatusService.registrerStatus(any(), any(), any(), any(), any()) } returns Unit
                with(
                    handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/status") {
                        setBody(objectMapper.writeValueAsString(SykmeldingStatusEventDTO(StatusEventDTO.AVBRUTT, OffsetDateTime.now(ZoneOffset.UTC))))
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        addHeader("FNR", "fnr")
                        addHeader("Authorization", "Bearer token")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Created
                }
            }
            it("Returerer BadRequest når man ikke kan endre status") {
                val sykmeldingId = "1234"
                coEvery { sykmeldingStatusService.registrerStatus(any(), any(), any(), any(), any()) } throws InvalidSykmeldingStatusException("Kan ikke endre status fra BEKREFTET til SEND for sykmeldingID $sykmeldingId")
                with(
                    handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/status") {
                        setBody(objectMapper.writeValueAsString(SykmeldingStatusEventDTO(StatusEventDTO.AVBRUTT, OffsetDateTime.now(ZoneOffset.UTC))))
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        addHeader("AUTHORIZATION", "Bearer token")
                        addHeader("FNR", "fnr")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                    response.content shouldBeEqualTo "Kan ikke endre status fra BEKREFTET til SEND for sykmeldingID $sykmeldingId"
                }
            }
            it("Returerer NotFound når status ikke finnes for bruker") {
                val sykmeldingId = "1234"
                coEvery { sykmeldingStatusService.registrerStatus(any(), any(), any(), any(), any()) } throws SykmeldingStatusNotFoundException("Fant ikke sykmeldingstatus for sykmelding id $sykmeldingId", RuntimeException("error"))
                with(
                    handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/status") {
                        setBody(objectMapper.writeValueAsString(SykmeldingStatusEventDTO(StatusEventDTO.AVBRUTT, OffsetDateTime.now(ZoneOffset.UTC))))
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        addHeader("AUTHORIZATION", "Bearer token")
                        addHeader("FNR", "fnr")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.NotFound
                    response.content shouldBeEqualTo "Fant ikke sykmeldingstatus for sykmelding id $sykmeldingId"
                }
            }
        }
    }

    describe("Test SykmeldingStatusAPI with security") {
        with(TestApplicationEngine()) {
            setUpTestApplication()

            val audience = listOf("loginserviceId1", "loginserviceId2")
            val env = getTestEnvironment(audience)

            val mockJwkProvider = mockkClass(JwkProvider::class)
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()

            application.setupAuth(audience, mockJwkProvider, "issuer1", env, jwkProvider)
            application.routing { authenticate("oidc") { registerSykmeldingStatusSyfoServiceApi(sykmeldingStatusService) } }

            it("Should authenticate") {
                val sykmeldingId = "123"
                val sykmeldingStatusEventDTO = SykmeldingStatusEventDTO(StatusEventDTO.AVBRUTT, OffsetDateTime.now(ZoneOffset.UTC))
                coEvery { sykmeldingStatusService.registrerStatus(any(), any(), any(), any(), any()) } returns Unit
                with(
                    handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/status") {
                        setBody(objectMapper.writeValueAsString(sykmeldingStatusEventDTO))
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        addHeader(
                            "AUTHORIZATION",
                            "Bearer ${generateJWT(
                                "client",
                                "preprod.local",
                                subject = "srvsyfoservice",
                                issuer = env.stsOidcIssuer
                            )}"
                        )
                        addHeader("FNR", "fnr")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Created
                }
            }
            it("Should not authenticate") {
                val sykmeldingStatusEventDTO = SykmeldingStatusEventDTO(StatusEventDTO.AVBRUTT, OffsetDateTime.now(ZoneOffset.UTC))
                with(
                    handleRequest(HttpMethod.Post, "/sykmeldinger/123/status") {
                        setBody(objectMapper.writeValueAsString(sykmeldingStatusEventDTO))
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        addHeader(
                            "Authorization",
                            "Bearer ${generateJWT(
                                "client",
                                "preprod.local",
                                subject = "srvsyforegister",
                                issuer = env.stsOidcIssuer
                            )}"
                        )
                        addHeader("FNR", "fnr")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
