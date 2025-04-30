package no.nav.syfo.arbeidsforhold.client.organisasjon.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import no.nav.sykmeldinger.arbeidsforhold.client.organisasjon.model.Organisasjonsinfo
import org.slf4j.LoggerFactory

class OrganisasjonsinfoClient(
    private val httpClient: HttpClient,
    private val url: String,
) {
    companion object {
        private val log = LoggerFactory.getLogger(OrganisasjonsinfoClient::class.java)
    }

    suspend fun getOrganisasjonsnavn(orgNummer: String): Organisasjonsinfo {
        try {
            return httpClient.get("$url/api/v1/organisasjon/$orgNummer/noekkelinfo").body()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av organisasjon $orgNummer fra ereg")
            throw e
        }
    }
}
