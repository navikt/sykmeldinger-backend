package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import no.nav.syfo.log
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusEventDTO

class SyfosmregisterClient(private val endpointUrl: String, private val httpClient: HttpClient) {

    suspend fun hentSykmeldingstatus(sykmeldingId: String, token: String): SykmeldingStatusEventDTO {
        log.info("Henter status for sykmeldingId {}", sykmeldingId)
        val httpResponse = httpClient.get<HttpStatement>("$endpointUrl/sykmeldinger/$sykmeldingId/status?filter=LATEST") {
            accept(ContentType.Application.Json)
            headers {
                append("Authorization", "Bearer $token")
                append("Nav-CallId", sykmeldingId)
            }
        }.execute()
        if (httpResponse.status == HttpStatusCode.InternalServerError) {
            log.error("Noe gikk galt ved sjekking av status eller tilgang for sykmeldingId {}", sykmeldingId)
            throw RuntimeException("Sykmeldingsregister svarte med feilmelding for $sykmeldingId")
        }
        when (HttpStatusCode.Forbidden) {
            httpResponse.status -> {
                log.warn("Bruker har ikke tilgang til sykmelding med id $sykmeldingId")
                throw RuntimeException("Sykmeldingsregister svarte med feilmelding for $sykmeldingId")
            }
            else -> {
                log.info("Hentet status for sykmeldingId {}", sykmeldingId)
                return httpResponse.call.response.receive()
            }
        }
    }
}
