package no.nav.syfo.sykmelding.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.ServerResponseException
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.api.SykmeldingDTO
import no.nav.syfo.sykmeldingstatus.getSykmeldingModel
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SyfosmregisterSykmeldingClientTest : Spek({

    var block: () -> HttpResponseData = {
        respondError(HttpStatusCode.InternalServerError)
    }
    fun setResponseData(responseData: HttpResponseData) {
        block = { responseData }
    }
    val httpClient = HttpClient(MockEngine) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        engine {
            addHandler { request ->
                block()
            }
        }
    }
    val syfosmregisterSykmeldingClient = SyfosmregisterSykmeldingClient("url", httpClient)

    describe("Test GET Sykmeldinger fra syfosmregister") {
        it("Should get empty list of Sykmeldinger") {
            setResponseData(respond(objectMapper.writeValueAsString(emptyList<SykmeldingDTO>()), HttpStatusCode.OK, headersOf("Content-Type", "application/json")))
            runBlocking {
                val result = syfosmregisterSykmeldingClient.getSykmeldinger("token")
                result shouldEqual emptyList()
            }
        }

        it("Should get list of sykmeldinger") {
            setResponseData(respond(objectMapper.writeValueAsString(listOf(getSykmeldingModel())), HttpStatusCode.OK, headersOf("Content-Type", "application/json")))
            runBlocking {
                val result = syfosmregisterSykmeldingClient.getSykmeldinger("token")
                result.size shouldEqual 1
            }
        }

        it("Should get InternalServerError") {
            setResponseData(respondError(HttpStatusCode.InternalServerError))
            runBlocking {
                val exception = assertFailsWith<ServerResponseException> {
                    syfosmregisterSykmeldingClient.getSykmeldinger("token")
                }
                exception.response.status shouldEqual HttpStatusCode.InternalServerError
            }
        }
        it("Should get Unauthorized") {
            setResponseData(respondError(HttpStatusCode.Unauthorized))
            runBlocking {
                val exception = assertFailsWith<ClientRequestException> {
                    syfosmregisterSykmeldingClient.getSykmeldinger("token")
                }
                exception.response.status shouldEqual HttpStatusCode.Unauthorized
            }
        }
        it("Should fail with forbidden") {
            setResponseData(respondError(HttpStatusCode.NotFound))
            runBlocking {
                val exception = assertFailsWith<ClientRequestException> {
                    syfosmregisterSykmeldingClient.getSykmeldinger("token")
                }
                exception.response.status shouldEqual HttpStatusCode.NotFound
            }
        }
    }
})
