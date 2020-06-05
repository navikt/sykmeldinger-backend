package no.nav.syfo.pdl.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PdlClientTest : Spek({
    var block: () -> HttpResponseData = {
        respondError(HttpStatusCode.NotFound)
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

    val graphQlQuery = File("src/main/resources/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), "")
    val pdlClient = PdlClient(httpClient, "graphqlend", graphQlQuery)

    describe("getPerson OK") {
        it("Skal få hente person fra pdl") {
            setResponseData(respond(getTestData(), HttpStatusCode.OK, headersOf("Content-Type", "application/json")))
            runBlocking {
                val response = pdlClient.getPerson("12345678901", "Bearer token", "Bearer token")
                response.data.hentPerson shouldNotEqual null
                response.data.hentPerson?.navn?.size shouldEqual 1
                response.data.hentPerson?.navn!![0].fornavn shouldEqual "RASK"
                response.data.hentPerson?.navn!![0].etternavn shouldEqual "SAKS"
            }
        }
        it("Skal få hentPerson = null ved error") {
            setResponseData(respond(getErrorResponse(), HttpStatusCode.OK, headersOf("Content-Type", "application/json")))
            runBlocking {
                val response = pdlClient.getPerson("12345678901", "Bearer token", "Bearer token")
                response.data.hentPerson shouldEqual null
            }
        }
    }
})
