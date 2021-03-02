package no.nav.syfo.pdl.client

import kotlinx.coroutines.runBlocking
import no.nav.syfo.pdl.client.model.Adressebeskyttelse
import no.nav.syfo.pdl.client.model.Gradering
import no.nav.syfo.testutils.HttpClientTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.File

class PdlClientTest : Spek({

    val httpClient = HttpClientTest()

    val graphQlQuery = File("src/main/resources/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), "")
    val pdlClient = PdlClient(httpClient.httpClient, "graphqlend", graphQlQuery)

    describe("getPerson OK") {
        it("Skal få hente person fra pdl") {
            httpClient.respond(getTestData())
            runBlocking {
                val response = pdlClient.getPerson("12345678901", "Bearer token", "Bearer token")
                response.data.person shouldNotBeEqualTo null
                response.data.person?.navn?.size shouldBeEqualTo 1
                response.data.person?.navn!![0].fornavn shouldBeEqualTo "RASK"
                response.data.person?.navn!![0].etternavn shouldBeEqualTo "SAKS"
                response.data.person?.adressebeskyttelse!![0] shouldBeEqualTo Adressebeskyttelse(Gradering.UGRADERT)
                val aktorId = response.data.identer?.identer?.find { it.gruppe == "AKTORID" }
                aktorId?.ident shouldBeEqualTo "99999999999"
            }
        }
        it("Skal få hentPerson = null ved error") {
            httpClient.respond(getErrorResponse())
            runBlocking {
                val response = pdlClient.getPerson("12345678901", "Bearer token", "Bearer token")
                response.data.person shouldBeEqualTo null
                response.errors?.size shouldBeEqualTo 1
                response.errors!![0].message shouldBeEqualTo "Ikke tilgang til å se person"
            }
        }
    }
})
