package no.nav.syfo.pdl.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import no.nav.syfo.log
import no.nav.syfo.metrics.HTTP_CLIENT_HISTOGRAM
import no.nav.syfo.pdl.client.model.GetPersonRequest
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.GetPersonVariables

class PdlClient(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val graphQlQuery: String
) {
    private val temaHeader = "TEMA"
    private val tema = "SYM"

    suspend fun getPersonTokenX(fnr: String, token: String): GetPersonResponse {
        val getPersonRequest = GetPersonRequest(query = graphQlQuery, variables = GetPersonVariables(ident = fnr))
        val timer = HTTP_CLIENT_HISTOGRAM.labels(basePath).startTimer()
        try {
            return httpClient.post(basePath) {
                setBody(getPersonRequest)
                header(HttpHeaders.Authorization, "Bearer $token")
                header(temaHeader, tema)
                header(HttpHeaders.ContentType, "application/json")
            }.body()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall til PDL", e)
            throw e
        } finally {
            timer.observeDuration()
        }
    }
}
