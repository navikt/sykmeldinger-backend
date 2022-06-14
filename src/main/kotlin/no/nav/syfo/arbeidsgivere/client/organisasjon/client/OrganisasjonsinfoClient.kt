package no.nav.syfo.arbeidsgivere.client.organisasjon.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import no.nav.syfo.arbeidsgivere.client.organisasjon.model.Organisasjonsinfo
import no.nav.syfo.log
import no.nav.syfo.metrics.HTTP_CLIENT_HISTOGRAM

class OrganisasjonsinfoClient(private val httpClient: HttpClient, private val url: String) {
    suspend fun getOrginfo(orgNummer: String): Organisasjonsinfo {
        val timer = HTTP_CLIENT_HISTOGRAM.labels("$url/api/v1/organisasjon/:orgNummer/noekkelinfo").startTimer()
        try {
            return httpClient.get("$url/api/v1/organisasjon/$orgNummer/noekkelinfo").body()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av organisasjon $orgNummer fra ereg")
            throw e
        } finally {
            timer.observeDuration()
        }
    }
}
