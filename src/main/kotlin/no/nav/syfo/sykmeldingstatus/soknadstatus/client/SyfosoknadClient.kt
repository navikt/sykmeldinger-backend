package no.nav.syfo.sykmeldingstatus.soknadstatus.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import no.nav.syfo.log

class SyfosoknadClient(private val endpointUrl: String, private val httpClient: HttpClient) {

    suspend fun getSoknader(token: String, sykmeldingId: String): List<RSSykepengesoknad> {
        try {
            return httpClient.get("$endpointUrl/api/soknader") {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", token)
                }
            }
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av søknad for sykmeldingId $sykmeldingId")
            throw e
        }
    }
}

data class RSSykepengesoknad(
    val id: String,
    val sykmeldingId: String?,
    val status: RSSoknadstatus?
)

enum class RSSoknadstatus {
    NY,
    SENDT,
    FREMTIDIG,
    UTKAST_TIL_KORRIGERING,
    KORRIGERT,
    AVBRUTT,
    UTGAATT,
    SLETTET
}
