package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.features.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import no.nav.syfo.log
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO

class SyfosmregisterStatusClient(private val endpointUrl: String, private val httpClient: HttpClient) {

    suspend fun hentSykmeldingstatus(sykmeldingId: String, token: String): SykmeldingStatusEventDTO {
        log.info("Henter status for sykmeldingId {}", sykmeldingId)
        try {
            val statusliste = httpClient.get<List<SykmeldingStatusEventDTO>>("$endpointUrl/sykmeldinger/$sykmeldingId/status?filter=LATEST") {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", token)
                    append("Nav-CallId", sykmeldingId)
                }
            }
            log.info("Hentet status for sykmeldingId {}", sykmeldingId)
            return statusliste.first()
        } catch (e: Exception) {
            if (e is ClientRequestException && e.response.status == HttpStatusCode.Forbidden) {
                log.warn("Bruker har ikke tilgang til sykmelding med id $sykmeldingId")
                throw RuntimeException("Sykmeldingsregister svarte med feilmelding for $sykmeldingId")
            } else {
                log.error("Noe gikk galt ved sjekking av status eller tilgang for sykmeldingId {}", sykmeldingId)
                throw RuntimeException("Sykmeldingsregister svarte med feilmelding for $sykmeldingId")
            }
        }
    }
}
