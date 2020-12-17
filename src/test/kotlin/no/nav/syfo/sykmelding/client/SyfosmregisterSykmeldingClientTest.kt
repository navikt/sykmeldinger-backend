package no.nav.syfo.sykmelding.client

import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.ServerResponseException
import io.ktor.http.HttpStatusCode
import java.time.LocalDate
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.api.ApiFilter
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmeldingstatus.getSykmeldingModel
import no.nav.syfo.testutils.HttpClientTest
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class SyfosmregisterSykmeldingClientTest : Spek({

    val httpClient = HttpClientTest()
    val endpointUrl = "url"
    val syfosmregisterSykmeldingClient = SyfosmregisterSykmeldingClient(endpointUrl, httpClient.httpClient)

    describe("Test GET Sykmeldinger fra syfosmregister") {
        it("Should get empty list of Sykmeldinger") {
            httpClient.respond(objectMapper.writeValueAsString(emptyList<SykmeldingDTO>()))
            runBlocking {
                val result = syfosmregisterSykmeldingClient.getSykmeldinger("token", null)
                result shouldEqual emptyList()
            }
        }

        it("Should get list of sykmeldinger") {
            httpClient.respond(objectMapper.writeValueAsString(listOf(getSykmeldingModel())))
            runBlocking {
                val result = syfosmregisterSykmeldingClient.getSykmeldinger("token", null)
                result.size shouldEqual 1
            }
        }

        it("Should get InternalServerError") {
            httpClient.respond(HttpStatusCode.InternalServerError)
            runBlocking {
                val exception = assertFailsWith<ServerResponseException> {
                    syfosmregisterSykmeldingClient.getSykmeldinger("token", null)
                }
                exception.response.status shouldEqual HttpStatusCode.InternalServerError
            }
        }
        it("Should get Unauthorized") {
            httpClient.respond(HttpStatusCode.Unauthorized)
            runBlocking {
                val exception = assertFailsWith<ClientRequestException> {
                    syfosmregisterSykmeldingClient.getSykmeldinger("token", null)
                }
                exception.response.status shouldEqual HttpStatusCode.Unauthorized
            }
        }
        it("Should fail with forbidden") {
            httpClient.respond(HttpStatusCode.NotFound)
            runBlocking {
                val exception = assertFailsWith<ClientRequestException> {
                    syfosmregisterSykmeldingClient.getSykmeldinger("token", null)
                }
                exception.response.status shouldEqual HttpStatusCode.NotFound
            }
        }
    }

    describe("Lager riktig request-url") {
        it("Får riktig url uten filter") {
            val url = syfosmregisterSykmeldingClient.getRequestUrl(ApiFilter(null, null, null, null))

            url shouldEqual "$endpointUrl/api/v2/sykmeldinger"
        }
        it("Får riktig url med fom og tom") {
            val url = syfosmregisterSykmeldingClient.getRequestUrl(ApiFilter(LocalDate.of(2020, 4, 1), LocalDate.of(2020, 4, 5), null, null))

            url shouldEqual "$endpointUrl/api/v2/sykmeldinger?fom=2020-04-01&tom=2020-04-05"
        }
        it("Får riktig url med fom og tom og exclude") {
            val url = syfosmregisterSykmeldingClient.getRequestUrl(ApiFilter(LocalDate.of(2020, 4, 1), LocalDate.of(2020, 4, 5), listOf("AVBRUTT"), null))

            url shouldEqual "$endpointUrl/api/v2/sykmeldinger?exclude=AVBRUTT&fom=2020-04-01&tom=2020-04-05"
        }
    }
})
