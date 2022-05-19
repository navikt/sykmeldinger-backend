package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

class SyfosmregisterClientSpek : Spek({

    val timestamp = OffsetDateTime.of(2020, 2, 2, 15, 0, 0, 0, ZoneOffset.UTC)
    val tokenXClient = mockk<TokenXClient>()

    val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(ContentNegotiation) {
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
                    else -> call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }.start()

    coEvery { tokenXClient.getAccessToken(any(), any()) } returns "token"

    val syfosmregisterClient = SyfosmregisterStatusClient("$mockHttpServerUrl/smreg", httpClient, tokenXClient, "audience")

    afterGroup {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    describe("Test av sykmeldingstatus-API") {
        it("Kan hente status for egen sykmelding med status APEN") {
            var sykmeldingStatusEventDTO: SykmeldingStatusEventDTO?
            runBlocking {
                sykmeldingStatusEventDTO = syfosmregisterClient.hentSykmeldingstatusTokenX("1", "token")
            }

            sykmeldingStatusEventDTO?.statusEvent shouldBeEqualTo StatusEventDTO.APEN
            sykmeldingStatusEventDTO?.timestamp shouldBeEqualTo timestamp
        }

        it("Kan hente status for egen sykmelding med status SENDT") {
            var sykmeldingStatusEventDTO: SykmeldingStatusEventDTO?
            runBlocking {
                sykmeldingStatusEventDTO = syfosmregisterClient.hentSykmeldingstatusTokenX("2", "token")
            }

            sykmeldingStatusEventDTO?.statusEvent shouldBeEqualTo StatusEventDTO.SENDT
            sykmeldingStatusEventDTO?.timestamp shouldBeEqualTo timestamp
        }

        it("Kan hente status for egen sykmelding med status BEKREFTET") {
            var sykmeldingStatusEventDTO: SykmeldingStatusEventDTO?
            runBlocking {
                sykmeldingStatusEventDTO = syfosmregisterClient.hentSykmeldingstatusTokenX("3", "token")
            }

            sykmeldingStatusEventDTO?.statusEvent shouldBeEqualTo StatusEventDTO.BEKREFTET
            sykmeldingStatusEventDTO?.timestamp shouldBeEqualTo timestamp
        }

        it("Kan hente status for egen sykmelding med status AVBRUTT") {
            var sykmeldingStatusEventDTO: SykmeldingStatusEventDTO?
            runBlocking {
                sykmeldingStatusEventDTO = syfosmregisterClient.hentSykmeldingstatusTokenX("4", "token")
            }

            sykmeldingStatusEventDTO?.statusEvent shouldBeEqualTo StatusEventDTO.AVBRUTT
            sykmeldingStatusEventDTO?.timestamp shouldBeEqualTo timestamp
        }

        it("Henting av status for annen brukers sykmelding gir feilmelding") {
            assertFailsWith<RuntimeException> {
                runBlocking {
                    syfosmregisterClient.hentSykmeldingstatusTokenX("5", "token")
                }
            }
        }

        it("Får feilmelding hvis syfosmregister svarer med feilmelding") {
            assertFailsWith<RuntimeException> {
                runBlocking {
                    syfosmregisterClient.hentSykmeldingstatusTokenX("6", "token")
                }
            }
        }
    }
})
