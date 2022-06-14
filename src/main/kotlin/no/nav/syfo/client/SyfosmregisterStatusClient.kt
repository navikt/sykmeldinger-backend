package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import no.nav.syfo.log
import no.nav.syfo.metrics.HTTP_CLIENT_HISTOGRAM
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO

class SyfosmregisterStatusClient(
    private val endpointUrl: String,
    private val httpClient: HttpClient,
    private val tokenXClient: TokenXClient,
    private val audience: String
) {
    suspend fun hentSykmeldingstatus(sykmeldingId: String, token: String): SykmeldingStatusEventDTO {
        val timer = HTTP_CLIENT_HISTOGRAM.labels("$endpointUrl/sykmeldinger/:sykmeldingId/status").startTimer()
        try {
            val statusliste = httpClient.get("$endpointUrl/sykmeldinger/$sykmeldingId/status?filter=LATEST") {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer $token")
                    append("Nav-CallId", sykmeldingId)
                }
            }
            log.info("Hentet status for sykmeldingId {}", sykmeldingId)
            return statusliste.body<List<SykmeldingStatusEventDTO>>().first()
        } catch (e: Exception) {
            if (e is ClientRequestException && e.response.status == HttpStatusCode.Forbidden) {
                log.warn("Bruker har ikke tilgang til sykmelding med id $sykmeldingId")
                throw RuntimeException("Sykmeldingsregister svarte med feilmelding for $sykmeldingId")
            } else {
                log.error("Noe gikk galt ved sjekking av status eller tilgang for sykmeldingId {}", sykmeldingId)
                throw RuntimeException("Sykmeldingsregister svarte med feilmelding for $sykmeldingId")
            }
        } finally {
            timer.observeDuration()
        }
    }

    suspend fun hentSykmeldingstatusTokenX(sykmeldingId: String, subjectToken: String): SykmeldingStatusEventDTO {
        val token = tokenXClient.getAccessToken(
            subjectToken = subjectToken,
            audience = audience
        )
        val timer = HTTP_CLIENT_HISTOGRAM.labels("$endpointUrl/api/v3/sykmeldinger/:sykmeldingId/status").startTimer()
        try {
            val statusliste = httpClient.get("$endpointUrl/api/v3/sykmeldinger/$sykmeldingId/status?filter=LATEST") {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer $token")
                    append("Nav-CallId", sykmeldingId)
                }
            }
            log.info("Hentet status for sykmeldingId {} med tokenx", sykmeldingId)
            return statusliste.body<List<SykmeldingStatusEventDTO>>().first()
        } catch (e: Exception) {
            if (e is ClientRequestException && e.response.status == HttpStatusCode.Forbidden) {
                log.warn("Bruker har ikke tilgang til sykmelding med id $sykmeldingId (tokenx)")
                throw RuntimeException("Sykmeldingsregister svarte med feilmelding for $sykmeldingId (tokenx)")
            } else {
                log.error("Noe gikk galt ved sjekking av status eller tilgang for sykmeldingId $sykmeldingId (tokenx)")
                throw RuntimeException("Sykmeldingsregister svarte med feilmelding for $sykmeldingId (tokenx)")
            }
        } finally {
            timer.observeDuration()
        }
    }
}
