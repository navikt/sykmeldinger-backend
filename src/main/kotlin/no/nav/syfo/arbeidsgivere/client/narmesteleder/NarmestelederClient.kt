package no.nav.syfo.arbeidsgivere.client.narmesteleder

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import java.time.LocalDate
import java.time.OffsetDateTime

class NarmestelederClient(
    private val httpClient: HttpClient,
    private val baseUrl: String
) {

    suspend fun getNarmesteledere(token: String): List<NarmesteLeder> {
        return httpClient.get<List<NarmesteLeder>>("$baseUrl/user/sykmeldt/narmesteledere") {
            headers {
                append(HttpHeaders.Authorization, token)
                append("Nav-Consumer-Id", "sykmeldinger-backend")
            }
            accept(ContentType.Application.Json)
        }
    }
}

data class NarmesteLeder(
    val orgnummer: String,
    val narmesteLederTelefonnummer: String,
    val narmesteLederEpost: String,
    val aktivFom: LocalDate,
    val aktivTom: LocalDate?,
    val arbeidsgiverForskutterer: Boolean?,
    val timestamp: OffsetDateTime,
    val navn: String?
)
