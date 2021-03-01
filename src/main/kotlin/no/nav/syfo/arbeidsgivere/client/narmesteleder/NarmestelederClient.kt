package no.nav.syfo.arbeidsgivere.client.narmesteleder

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.syfo.application.azuread.AccessTokenClient
import java.time.LocalDate

class NarmestelederClient(
    private val httpClient: HttpClient,
    private val accessTokenClient: AccessTokenClient,
    private val baseUrl: String
) {

    suspend fun getNarmesteledere(sykmeldtAktorId: String): List<NarmesteLederRelasjon> {
        val token = accessTokenClient.getAccessToken()
        return httpClient.get<List<NarmesteLederRelasjon>>("$baseUrl/syfonarmesteleder/sykmeldt/$sykmeldtAktorId/narmesteledere?utvidet=ja") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
                append("Nav-Consumer-Id", "sykmeldinger-backend")
            }
            accept(ContentType.Application.Json)
        }
    }
}

data class NarmesteLederRelasjon(
    val aktorId: String,
    val orgnummer: String,
    val narmesteLederAktorId: String,
    val narmesteLederTelefonnummer: String?,
    val narmesteLederEpost: String?,
    val aktivFom: LocalDate,
    val aktivTom: LocalDate?,
    val arbeidsgiverForskutterer: Boolean?,
    val skrivetilgang: Boolean?,
    val tilganger: List<String>?,
    val navn: String?
)
