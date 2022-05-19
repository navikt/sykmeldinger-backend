package no.nav.syfo.sykmelding.client

import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.ServerResponseException
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.TokenXClient
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.api.ApiFilter
import no.nav.syfo.sykmelding.model.Sykmelding
import no.nav.syfo.sykmeldingstatus.getSykmeldingModel
import no.nav.syfo.testutils.HttpClientTest
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import kotlin.test.assertFailsWith

class SyfosmregisterSykmeldingClientTest : Spek({

    val httpClient = HttpClientTest()
    val endpointUrl = "url"
    val tokenXClient = mockk<TokenXClient>()
    val syfosmregisterSykmeldingClient = SyfosmregisterSykmeldingClient(endpointUrl, httpClient.httpClient, tokenXClient, "audience")

    beforeEachTest {
        coEvery { tokenXClient.getAccessToken(any(), any()) } returns "token"
    }

    describe("Test GET Sykmeldinger fra syfosmregister") {
        it("Should get empty list of Sykmeldinger") {
            httpClient.respond(objectMapper.writeValueAsString(emptyList<Sykmelding>()))
            runBlocking {
                val result = syfosmregisterSykmeldingClient.getSykmeldingerTokenX("token", null)
                result shouldBeEqualTo emptyList()
            }
        }

        it("Should get list of sykmeldinger") {
            httpClient.respond(objectMapper.writeValueAsString(listOf(getSykmeldingModel())))
            runBlocking {
                val result = syfosmregisterSykmeldingClient.getSykmeldingerTokenX("token", null)
                result.size shouldBeEqualTo 1
            }
        }

        it("Should get InternalServerError") {
            httpClient.respond(HttpStatusCode.InternalServerError)
            runBlocking {
                val exception = assertFailsWith<ServerResponseException> {
                    syfosmregisterSykmeldingClient.getSykmeldingerTokenX("token", null)
                }
                exception.response.status shouldBeEqualTo HttpStatusCode.InternalServerError
            }
        }
        it("Should get Unauthorized") {
            httpClient.respond(HttpStatusCode.Unauthorized)
            runBlocking {
                val exception = assertFailsWith<ClientRequestException> {
                    syfosmregisterSykmeldingClient.getSykmeldingerTokenX("token", null)
                }
                exception.response.status shouldBeEqualTo HttpStatusCode.Unauthorized
            }
        }
        it("Should fail with forbidden") {
            httpClient.respond(HttpStatusCode.NotFound)
            runBlocking {
                val exception = assertFailsWith<ClientRequestException> {
                    syfosmregisterSykmeldingClient.getSykmeldingerTokenX("token", null)
                }
                exception.response.status shouldBeEqualTo HttpStatusCode.NotFound
            }
        }
    }

    describe("Lager riktig request-url") {
        it("Får riktig url uten filter") {
            val url = syfosmregisterSykmeldingClient.getRequestUrl(ApiFilter(null, null, null, null), "$endpointUrl/api/v3")

            url shouldBeEqualTo "$endpointUrl/api/v3/sykmeldinger"
        }
        it("Får riktig url med fom og tom") {
            val url = syfosmregisterSykmeldingClient.getRequestUrl(
                ApiFilter(
                    LocalDate.of(2020, 4, 1),
                    LocalDate.of(2020, 4, 5),
                    null,
                    null
                ),
                "$endpointUrl/api/v3"
            )

            url shouldBeEqualTo "$endpointUrl/api/v3/sykmeldinger?fom=2020-04-01&tom=2020-04-05"
        }
        it("Får riktig url med fom og tom og exclude") {
            val url = syfosmregisterSykmeldingClient.getRequestUrl(
                ApiFilter(
                    LocalDate.of(2020, 4, 1),
                    LocalDate.of(2020, 4, 5),
                    listOf("AVBRUTT"),
                    null
                ),
                "$endpointUrl/api/v3"
            )

            url shouldBeEqualTo "$endpointUrl/api/v3/sykmeldinger?exclude=AVBRUTT&fom=2020-04-01&tom=2020-04-05"
        }
    }
})
