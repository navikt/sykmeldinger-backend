package no.nav.syfo.sykmelding.client

import io.kotest.core.spec.style.FunSpec
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import no.nav.syfo.client.TokenXClient
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.api.ApiFilter
import no.nav.syfo.sykmelding.model.Sykmelding
import no.nav.syfo.sykmeldingstatus.getSykmeldingModel
import no.nav.syfo.testutils.HttpClientTest
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate
import kotlin.test.assertFailsWith

class SyfosmregisterSykmeldingClientTest : FunSpec({

    val httpClient = HttpClientTest()
    val endpointUrl = "url"
    val tokenXClient = mockk<TokenXClient>()
    val syfosmregisterSykmeldingClient = SyfosmregisterSykmeldingClient(endpointUrl, httpClient.httpClient, tokenXClient, "audience")

    context("Test GET Sykmeldinger fra syfosmregister") {
        test("Should get empty list of Sykmeldinger") {
            httpClient.respond(objectMapper.writeValueAsString(emptyList<Sykmelding>()))

            val result = syfosmregisterSykmeldingClient.getSykmeldinger("token", null)
            result shouldBeEqualTo emptyList()
        }

        test("Should get list of sykmeldinger") {
            httpClient.respond(objectMapper.writeValueAsString(listOf(getSykmeldingModel())))

            val result = syfosmregisterSykmeldingClient.getSykmeldinger("token", null)
            result.size shouldBeEqualTo 1
        }

        test("Should get InternalServerError") {
            httpClient.respond(HttpStatusCode.InternalServerError)

            val exception = assertFailsWith<ServerResponseException> {
                syfosmregisterSykmeldingClient.getSykmeldinger("token", null)
            }
            exception.response.status shouldBeEqualTo HttpStatusCode.InternalServerError
        }
        test("Should get Unauthorized") {
            httpClient.respond(HttpStatusCode.Unauthorized)

            val exception = assertFailsWith<ClientRequestException> {
                syfosmregisterSykmeldingClient.getSykmeldinger("token", null)
            }
            exception.response.status shouldBeEqualTo HttpStatusCode.Unauthorized
        }
        test("Should fail with forbidden") {
            httpClient.respond(HttpStatusCode.NotFound)

            val exception = assertFailsWith<ClientRequestException> {
                syfosmregisterSykmeldingClient.getSykmeldinger("token", null)
            }
            exception.response.status shouldBeEqualTo HttpStatusCode.NotFound
        }
    }

    context("Lager riktig request-url") {
        test("Får riktig url uten filter") {
            val url = syfosmregisterSykmeldingClient.getRequestUrl(ApiFilter(null, null, null, null), "$endpointUrl/api/v2")

            url shouldBeEqualTo "$endpointUrl/api/v2/sykmeldinger"
        }
        test("Får riktig url med fom og tom") {
            val url = syfosmregisterSykmeldingClient.getRequestUrl(
                ApiFilter(
                    LocalDate.of(2020, 4, 1),
                    LocalDate.of(2020, 4, 5),
                    null,
                    null
                ),
                "$endpointUrl/api/v2"
            )

            url shouldBeEqualTo "$endpointUrl/api/v2/sykmeldinger?fom=2020-04-01&tom=2020-04-05"
        }
        test("Får riktig url med fom og tom og exclude") {
            val url = syfosmregisterSykmeldingClient.getRequestUrl(
                ApiFilter(
                    LocalDate.of(2020, 4, 1),
                    LocalDate.of(2020, 4, 5),
                    listOf("AVBRUTT"),
                    null
                ),
                "$endpointUrl/api/v2"
            )

            url shouldBeEqualTo "$endpointUrl/api/v2/sykmeldinger?exclude=AVBRUTT&fom=2020-04-01&tom=2020-04-05"
        }
    }
})
