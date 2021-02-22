package no.nav.syfo.pdl.client

import kotlinx.coroutines.runBlocking
import no.nav.syfo.testutils.HttpClientTest
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
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
                response.data.hentPerson shouldNotEqual null
                response.data.hentPerson?.navn?.size shouldEqual 1
                response.data.hentPerson?.navn!![0].fornavn shouldEqual "RASK"
                response.data.hentPerson?.navn!![0].etternavn shouldEqual "SAKS"
            }
        }
        it("Skal få hentPerson = null ved error") {
            httpClient.respond(getErrorResponse())
            runBlocking {
                val response = pdlClient.getPerson("12345678901", "Bearer token", "Bearer token")
                response.data.hentPerson shouldEqual null
                response.errors?.size shouldEqual 1
                response.errors!![0].message shouldEqual "Ikke tilgang til å se person"
            }
        }
    }
})
