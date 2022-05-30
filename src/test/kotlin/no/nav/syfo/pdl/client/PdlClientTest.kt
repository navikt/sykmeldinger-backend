package no.nav.syfo.pdl.client

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.pdl.client.model.Adressebeskyttelse
import no.nav.syfo.pdl.client.model.Gradering
import no.nav.syfo.testutils.HttpClientTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import java.io.File

class PdlClientTest : FunSpec({

    val httpClient = HttpClientTest()

    val graphQlQuery = File("src/main/resources/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), "")
    val pdlClient = PdlClient(httpClient.httpClient, "graphqlend", graphQlQuery)

    context("getPerson OK") {
        test("Skal få hente person fra pdl") {
            httpClient.respond(getTestData())

            val response = pdlClient.getPersonTokenX("12345678901", "Bearer token")
            response.data.person shouldNotBeEqualTo null
            response.data.person?.navn?.size shouldBeEqualTo 1
            response.data.person?.navn!![0].fornavn shouldBeEqualTo "RASK"
            response.data.person?.navn!![0].etternavn shouldBeEqualTo "SAKS"
            response.data.person?.adressebeskyttelse!![0] shouldBeEqualTo Adressebeskyttelse(Gradering.UGRADERT)
            val aktorId = response.data.identer?.identer?.find { it.gruppe == "AKTORID" }
            aktorId?.ident shouldBeEqualTo "99999999999"
        }
        test("Skal få hentPerson = null ved error") {
            httpClient.respond(getErrorResponse())

            val response = pdlClient.getPersonTokenX("12345678901", "Bearer token")
            response.data.person shouldBeEqualTo null
            response.errors?.size shouldBeEqualTo 1
            response.errors!![0].message shouldBeEqualTo "Ikke tilgang til å se person"
        }
    }
})
