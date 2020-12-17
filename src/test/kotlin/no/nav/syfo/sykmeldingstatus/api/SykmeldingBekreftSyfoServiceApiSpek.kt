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
import java.nio.file.Paths
import no.nav.syfo.Environment
import no.nav.syfo.application.setupAuth
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.testutils.generateJWT
import no.nav.syfo.testutils.setUpTestApplication
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingBekreftSyfoServiceApiSpek : Spek({

    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)

    describe("Test SykmeldingBekreftAPI") {
        with(TestApplicationEngine()) {
            setUpTestApplication()
            application.routing { registerSykmeldingBekreftSyfoServiceApi(sykmeldingStatusService) }

            it("Skal returnere Created hvis alt går bra") {
                val sykmeldingId = "123"
                coEvery { sykmeldingStatusService.registrerBekreftet(any(), any(), any(), any(), any()) } returns Unit
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/bekreft") {
                    setBody(objectMapper.writeValueAsString(opprettSykmeldingBekreftEventDTO()))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                    addHeader("AUTHORIZATION", "Bearer token")
                    addHeader("FNR", "fnr")
                }) {
                    response.status() shouldEqual HttpStatusCode.Created
                }
            }
            it("Returnerer 500 hvis noe går galt") {
                val sykmeldingId = "1235"
                coEvery { sykmeldingStatusService.registrerBekreftet(any(), any(), any(), any(), any()) } throws RuntimeException("Noe gikk galt")
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/bekreft") {
                    setBody(objectMapper.writeValueAsString(opprettSykmeldingBekreftEventDTO()))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                    addHeader("AUTHORIZATION", "Bearer token")
                    addHeader("FNR", "fnr")
                }) {
                    response.status() shouldEqual HttpStatusCode.InternalServerError
                }
            }

            it("Returerer BadRequest når man ikke kan endre status") {
                val sykmeldingId = "1234"
                coEvery { sykmeldingStatusService.registrerBekreftet(any(), any(), any(), any(), any()) } throws InvalidSykmeldingStatusException("Kan ikke endre status fra SENDT til BEKREFT for sykmeldingID $sykmeldingId")
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/bekreft") {
                    setBody(objectMapper.writeValueAsString(opprettSykmeldingBekreftEventDTO()))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                    addHeader("AUTHORIZATION", "Bearer token")
                    addHeader("FNR", "fnr")
                }) {
                    response.status() shouldEqual HttpStatusCode.BadRequest
                    response.content shouldEqual "Kan ikke endre status fra SENDT til BEKREFT for sykmeldingID $sykmeldingId"
                }
            }
            it("Returerer NotFound når status ikke finnes for bruker") {
                val sykmeldingId = "1234"
                coEvery { sykmeldingStatusService.registrerBekreftet(any(), any(), any(), any(), any()) } throws SykmeldingStatusNotFoundException("Fant ikke sykmeldingstatus for sykmelding id $sykmeldingId", RuntimeException("error"))
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/bekreft") {
                    setBody(objectMapper.writeValueAsString(opprettSykmeldingBekreftEventDTO()))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                    addHeader("AUTHORIZATION", "Bearer token")
                    addHeader("FNR", "fnr")
                }) {
                    response.status() shouldEqual HttpStatusCode.NotFound
                    response.content shouldEqual "Fant ikke sykmeldingstatus for sykmelding id $sykmeldingId"
                }
            }
        }
    }

    describe("Test SykmeldingBekreftAPI med tilgangskontroll") {
        with(TestApplicationEngine()) {
            setUpTestApplication()

            val audience = listOf("loginserviceId1", "loginserviceId2")
            val env = Environment(
                jwtIssuer = "issuer",
                kafkaBootstrapServers = "",
                stsOidcIssuer = "https://security-token-service.nais.preprod.local",
                stsOidcAudience = "preprod.local",
                pdlGraphqlPath = "http://graphql",
                cluster = "dev-fss",
                loginserviceIdportenDiscoveryUrl = "url",
                loginserviceIdportenAudience = audience,
                truststore = "",
                truststorePassword = ""
            )

            val mockJwkProvider = mockkClass(JwkProvider::class)
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()

            application.setupAuth(audience, mockJwkProvider, "issuer1", env, jwkProvider)
            application.routing { authenticate("oidc") { registerSykmeldingBekreftSyfoServiceApi(sykmeldingStatusService) } }

            it("Should authenticate") {
                val sykmeldingId = "123"
                coEvery { sykmeldingStatusService.registrerBekreftet(any(), any(), any(), any(), any()) } returns Unit
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/bekreft") {
                    setBody(objectMapper.writeValueAsString(opprettSykmeldingBekreftEventDTO()))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                    addHeader("AUTHORIZATION", "Bearer ${
                        generateJWT("client",
                            "preprod.local",
                            subject = "srvsyfoservice",
                            issuer = env.stsOidcIssuer)
                    }")
                    addHeader("FNR", "fnr")
                }) {
                    response.status() shouldEqual HttpStatusCode.Created
                }
            }
            it("Should not authenticate") {
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/123/bekreft") {
                    setBody(objectMapper.writeValueAsString(opprettSykmeldingBekreftEventDTO()))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                    addHeader("Authorization", "Bearer ${
                        generateJWT(
                            "client",
                            "preprod.local",
                            subject = "srvsyforegister",
                            issuer = env.stsOidcIssuer)
                    }")
                    addHeader("FNR", "fnr")
                }) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }
        }
    }
})
