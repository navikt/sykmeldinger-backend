package no.nav.syfo.arbeidsgivere.client.narmesteleder

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import no.nav.syfo.client.TokenXClient
import no.nav.syfo.log
import java.time.LocalDate
import java.time.OffsetDateTime

class NarmestelederClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val tokenXClient: TokenXClient,
    private val audience: String
) {
    suspend fun getNarmesteledereTokenX(subjectToken: String): List<NarmesteLeder> {
        val token = tokenXClient.getAccessToken(
            subjectToken = subjectToken,
            audience = audience
        )
        try {
            return httpClient.get("$baseUrl/user/v2/sykmeldt/narmesteledere") {
                headers {
                    append(HttpHeaders.Authorization, token)
                    append("Nav-Consumer-Id", "sykmeldinger-backend")
                }
                accept(ContentType.Application.Json)
            }.body<List<NarmesteLeder>>()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av nærmeste leder")
            throw e
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
