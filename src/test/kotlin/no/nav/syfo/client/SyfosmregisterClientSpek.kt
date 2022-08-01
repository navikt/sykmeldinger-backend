package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.getCioClient
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.tokenx.TokenXClient
import org.amshove.kluent.shouldBeEqualTo
import java.net.ServerSocket
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

class SyfosmregisterClientSpek : FunSpec({

    val timestamp = OffsetDateTime.of(2020, 2, 2, 15, 0, 0, 0, ZoneOffset.UTC)
    val tokenXClient = mockk<TokenXClient>()

    val httpClient = getCioClient()

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        routing {
            get("/smreg/api/v3/sykmeldinger/{sykmeldingsid}/status") {
                when {
                    call.parameters["sykmeldingsid"] == "1" ->
                        call.respond(listOf(SykmeldingStatusEventDTO(StatusEventDTO.APEN, timestamp)))

                    call.parameters["sykmeldingsid"] == "2" ->
                        call.respond(listOf(SykmeldingStatusEventDTO(StatusEventDTO.SENDT, timestamp)))

                    call.parameters["sykmeldingsid"] == "3" ->
                        call.respond(listOf(SykmeldingStatusEventDTO(StatusEventDTO.BEKREFTET, timestamp)))

                    call.parameters["sykmeldingsid"] == "4" ->
                        call.respond(listOf(SykmeldingStatusEventDTO(StatusEventDTO.AVBRUTT, timestamp)))

                    call.parameters["sykmeldingsid"] == "5" ->
                        call.respond(HttpStatusCode.Forbidden)

                    call.parameters["sykmeldingsid"] == "6" ->
                        call.respond(HttpStatusCode.GatewayTimeout)

                    else -> call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }.start()

    val syfosmregisterClient =
        SyfosmregisterStatusClient("$mockHttpServerUrl/smreg", httpClient, tokenXClient, "audience")

    coEvery { tokenXClient.getAccessToken(any(), any()) } returns "token"

    afterSpec {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }
    context("Test errorcodes") {
        test("Should retry when statuscode is GatewayTimeout") {
            assertFailsWith<ServerResponseException> {
                syfosmregisterClient.hentSykmeldingstatusTokenX("6", "token")
            }
        }
    }
    context("Test av sykmeldingstatus-API") {
        test("Kan hente status for egen sykmelding med status APEN") {
            val sykmeldingStatusEventDTO = syfosmregisterClient.hentSykmeldingstatusTokenX("1", "token")

            sykmeldingStatusEventDTO.statusEvent shouldBeEqualTo StatusEventDTO.APEN
            sykmeldingStatusEventDTO.timestamp shouldBeEqualTo timestamp
        }

        test("Kan hente status for egen sykmelding med status SENDT") {
            val sykmeldingStatusEventDTO = syfosmregisterClient.hentSykmeldingstatusTokenX("2", "token")

            sykmeldingStatusEventDTO.statusEvent shouldBeEqualTo StatusEventDTO.SENDT
            sykmeldingStatusEventDTO.timestamp shouldBeEqualTo timestamp
        }

        test("Kan hente status for egen sykmelding med status BEKREFTET") {
            val sykmeldingStatusEventDTO = syfosmregisterClient.hentSykmeldingstatusTokenX("3", "token")

            sykmeldingStatusEventDTO.statusEvent shouldBeEqualTo StatusEventDTO.BEKREFTET
            sykmeldingStatusEventDTO.timestamp shouldBeEqualTo timestamp
        }

        test("Kan hente status for egen sykmelding med status AVBRUTT") {
            val sykmeldingStatusEventDTO = syfosmregisterClient.hentSykmeldingstatusTokenX("4", "token")

            sykmeldingStatusEventDTO.statusEvent shouldBeEqualTo StatusEventDTO.AVBRUTT
            sykmeldingStatusEventDTO.timestamp shouldBeEqualTo timestamp
        }

        test("Henting av status for annen brukers sykmelding gir feilmelding") {
            assertFailsWith<RuntimeException> {
                runBlocking {
                    syfosmregisterClient.hentSykmeldingstatusTokenX("5", "token")
                }
            }
        }

        test("Får feilmelding hvis syfosmregister svarer med feilmelding") {
            assertFailsWith<RuntimeException> {
                runBlocking {
                    syfosmregisterClient.hentSykmeldingstatusTokenX("6", "token")
                }
            }
        }
    }
})
