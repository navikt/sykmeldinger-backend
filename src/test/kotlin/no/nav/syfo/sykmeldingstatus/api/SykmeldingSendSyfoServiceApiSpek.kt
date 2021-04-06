package no.nav.syfo.sykmeldingstatus.api

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
import io.mockk.coEvery
import io.mockk.mockkClass
import no.nav.syfo.application.setupAuth
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.registerSykmeldingSendSyfoServiceApi
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

class SykmeldingSendSyfoServiceApiSpek : Spek({

    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)

    describe("Test SykmeldingSendAPI") {
        with(TestApplicationEngine()) {
            setUpTestApplication()

            application.routing { registerSykmeldingSendSyfoServiceApi(sykmeldingStatusService) }

            it("Skal returnere Created hvis alt går bra") {
                val sykmeldingId = "123"
                coEvery { sykmeldingStatusService.registrerSendt(any(), any(), any(), any(), any(), any()) } returns Unit
                with(
                    handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/send") {
                        setBody(objectMapper.writeValueAsString(opprettSykmeldingSendEventDTO()))
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        addHeader("FNR", "fnr")
                        addHeader("Authorization", "Bearer token")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Created
                }
            }
            it("Returnerer 500 hvis noe går galt") {
                val sykmeldingId = "1235"
                coEvery { sykmeldingStatusService.registrerSendt(any(), any(), any(), any(), any(), any()) } throws RuntimeException("Noe gikk galt")
                with(
                    handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/send") {
                        setBody(objectMapper.writeValueAsString(opprettSykmeldingSendEventDTO()))
                        addHeader("Content-Type", ContentType.Application.Json.toString())
                        addHeader("FNR", "fnr")
                        addHeader("Authorization", "Bearer token")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                }
            }
            it("Returerer BadRequest når man ikke kan endre status") {
                val sykmeldingId = "1234"
                coEvery { sykmeldingStatusService.registrerSendt(any(), any(), any(), any(), any(), any()) } throws InvalidSykmeldingStatusException("Kan ikke endre status fra BEKREFTET til SEND for sykmeldingID $sykmeldingId")
                with(
                    handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/send") {
                        setBody(objectMapper.writeValueAsString(opprettSykmeldingSendEventDTO()))
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
                coEvery { sykmeldingStatusService.registrerSendt(any(), any(), any(), any(), any(), any()) } throws SykmeldingStatusNotFoundException("Fant ikke sykmeldingstatus for sykmelding id $sykmeldingId", RuntimeException("error"))
                with(
                    handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/send") {
                        setBody(objectMapper.writeValueAsString(opprettSykmeldingSendEventDTO()))
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

    describe("Test SykmeldingSendAPI med tilgangskontroll") {
        with(TestApplicationEngine()) {
            setUpTestApplication()

            val audience = listOf("loginserviceId1", "loginserviceId2")
            val env = getTestEnvironment(audience)

            val mockJwkProvider = mockkClass(JwkProvider::class)
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()

            application.setupAuth(audience, mockJwkProvider, "issuer1", env, jwkProvider)
            application.routing { authenticate("oidc") { registerSykmeldingSendSyfoServiceApi(sykmeldingStatusService) } }

            it("Should authenticate") {
                val sykmeldingId = "123"
                coEvery { sykmeldingStatusService.registrerSendt(any(), any(), any(), any(), any(), any()) } returns Unit
                with(
                    handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/send") {
                        setBody(objectMapper.writeValueAsString(opprettSykmeldingSendEventDTO()))
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
                with(
                    handleRequest(HttpMethod.Post, "/sykmeldinger/123/send") {
                        setBody(objectMapper.writeValueAsString(opprettSykmeldingSendEventDTO()))
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

fun opprettSykmeldingSendEventDTO(): SykmeldingSendEventDTO =
        SykmeldingSendEventDTO(
                OffsetDateTime.now(ZoneOffset.UTC),
                ArbeidsgiverStatusDTO(orgnummer = "orgnummer", juridiskOrgnummer = null, orgNavn = "navn"),
                listOf(
                        SporsmalOgSvarDTO("", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "ARBEIDSTAKER"),
                        SporsmalOgSvarDTO("", ShortNameDTO.NY_NARMESTE_LEDER, SvartypeDTO.JA_NEI, "NEI")
                )
        )
