package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.response.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import no.nav.syfo.log
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusEventDTO

class SyfosmregisterStatusClient(private val endpointUrl: String, private val httpClient: HttpClient) {

    suspend fun hentSykmeldingstatus(sykmeldingId: String, token: String): SykmeldingStatusEventDTO {
        log.info("Henter status for sykmeldingId {}", sykmeldingId)
        val httpResponse = httpClient.get<HttpResponse>("$endpointUrl/sykmeldinger/$sykmeldingId/status?filter=LATEST") {
            accept(ContentType.Application.Json)
            headers {
                append("Authorization", token)
                append("Nav-CallId", sykmeldingId)
            }
        }
        when (httpResponse.status) {
            HttpStatusCode.InternalServerError -> {
                log.error("Noe gikk galt ved sjekking av status eller tilgang for sykmeldingId {}", sykmeldingId)
                throw RuntimeException("Sykmeldingsregister svarte med feilmelding for $sykmeldingId")
            }
            HttpStatusCode.Forbidden -> {
                log.warn("Bruker har ikke tilgang til sykmelding med id $sykmeldingId")
                throw RuntimeException("Sykmeldingsregister svarte med feilmelding for $sykmeldingId")
            }
            else -> {
                log.info("Hentet status for sykmeldingId {}", sykmeldingId)
                val statusliste: List<SykmeldingStatusEventDTO> = httpResponse.call.response.receive()
                return statusliste.first()
            }
        }
    }
}
