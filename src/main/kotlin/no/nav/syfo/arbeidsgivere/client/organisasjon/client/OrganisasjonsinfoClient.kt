package no.nav.syfo.arbeidsgivere.client.organisasjon.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import no.nav.syfo.arbeidsgivere.client.organisasjon.model.Organisasjonsinfo
import no.nav.syfo.log

class OrganisasjonsinfoClient(private val httpClient: HttpClient, private val basePath: String) {
    suspend fun getOrginfo(orgNummer: String): Organisasjonsinfo {
        try {
            return httpClient.get("$basePath/ereg/api/v1/organisasjon/$orgNummer/noekkelinfo")
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av organisasjon $orgNummer fra ereg")
            throw e
        }
    }
}
