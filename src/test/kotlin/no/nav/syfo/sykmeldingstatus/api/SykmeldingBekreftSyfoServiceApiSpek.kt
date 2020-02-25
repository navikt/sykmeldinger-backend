package no.nav.syfo.sykmeldingstatus.api

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.every
import io.mockk.mockkClass
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.application.setupAuth
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.testutils.generateJWT
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SykmeldingBekreftSyfoServiceApiSpek : Spek({

    val sykmeldingStatusService = mockkClass(SykmeldingStatusService::class)

    describe("Test SykmeldingBekreftAPI") {
        with(TestApplicationEngine()) {
            start(true)
            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
            application.routing { registerSykmeldingBekreftSyfoServiceApi(sykmeldingStatusService) }

            it("Skal returnere Created hvis alt går bra") {
                val sykmeldingId = "123"
                every { sykmeldingStatusService.registrerBekreftet(any(), any(), any(), any()) } returns Unit
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/bekreft") {
                    setBody(objectMapper.writeValueAsString(opprettSykmeldingBekreftEventDTO()))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                    addHeader("FNR", "fnr")
                }) {
                    response.status() shouldEqual HttpStatusCode.Created
                }
            }
            it("Returnerer 500 hvis noe går galt") {
                val sykmeldingId = "1235"
                every { sykmeldingStatusService.registrerBekreftet(any(), any(), any(), any()) } throws RuntimeException("Noe gikk galt")
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/bekreft") {
                    setBody(objectMapper.writeValueAsString(opprettSykmeldingBekreftEventDTO()))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                    addHeader("FNR", "fnr")
                }) {
                    response.status() shouldEqual HttpStatusCode.InternalServerError
                }
            }
        }
    }

    describe("Test SykmeldingBekreftAPI med tilgangskontroll") {
        with(TestApplicationEngine()) {
            start(true)
            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }

            val env = Environment(jwtIssuer = "issuer",
                    kafkaBootstrapServers = "",
                    stsOidcIssuer = "https://security-token-service.nais.preprod.local",
                    stsOidcAudience = "preprod.local")

            val mockJwkProvider = mockkClass(JwkProvider::class)
            val path = "src/test/resources/jwkset.json"
            val uri = Paths.get(path).toUri().toURL()
            val jwkProvider = JwkProviderBuilder(uri).build()
            val vaultSecrets = VaultSecrets("", "", "1", "", "", "")

            application.setupAuth(vaultSecrets, mockJwkProvider, "issuer1", env, jwkProvider)
            application.routing { authenticate("oidc") { registerSykmeldingBekreftSyfoServiceApi(sykmeldingStatusService) } }

            it("Should authenticate") {
                val sykmeldingId = "123"
                every { sykmeldingStatusService.registrerBekreftet(any(), any(), any(), any()) } returns Unit
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/$sykmeldingId/bekreft") {
                    setBody(objectMapper.writeValueAsString(opprettSykmeldingBekreftEventDTO()))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                    addHeader("AUTHORIZATION", "Bearer ${generateJWT("client",
                            "preprod.local",
                            subject = "srvsyfoservice",
                            issuer = env.stsOidcIssuer)}")
                    addHeader("FNR", "fnr")
                }) {
                    response.status() shouldEqual HttpStatusCode.Created
                }
            }
            it("Should not authenticate") {
                with(handleRequest(HttpMethod.Post, "/sykmeldinger/123/bekreft") {
                    setBody(objectMapper.writeValueAsString(opprettSykmeldingBekreftEventDTO()))
                    addHeader("Content-Type", ContentType.Application.Json.toString())
                    addHeader("Authorization", "Bearer ${generateJWT(
                            "client",
                            "preprod.local",
                            subject = "srvsyforegister",
                            issuer = env.stsOidcIssuer)}")
                    addHeader("FNR", "fnr")
                }) {
                    response.status() shouldEqual HttpStatusCode.Unauthorized
                }
            }
        }
    }
})

fun opprettSykmeldingBekreftEventDTO(): SykmeldingBekreftEventDTO =
    SykmeldingBekreftEventDTO(
        OffsetDateTime.now(ZoneOffset.UTC),
        listOf(SporsmalOgSvarDTO("Sykmeldt fra ", ShortNameDTO.ARBEIDSSITUASJON, SvartypeDTO.ARBEIDSSITUASJON, "Frilanser"),
            SporsmalOgSvarDTO("Har forsikring?", ShortNameDTO.FORSIKRING, SvartypeDTO.JA_NEI, "Ja"),
            SporsmalOgSvarDTO("Hatt fravær?", ShortNameDTO.FRAVAER, SvartypeDTO.JA_NEI, "Ja"),
            SporsmalOgSvarDTO("Når hadde du fravær?", ShortNameDTO.PERIODE, SvartypeDTO.PERIODER, "{[{\"fom\": \"2019-8-1\", \"tom\": \"2019-8-15\"}, {\"fom\": \"2019-9-1\", \"tom\": \"2019-9-3\"}]}"))
    )
