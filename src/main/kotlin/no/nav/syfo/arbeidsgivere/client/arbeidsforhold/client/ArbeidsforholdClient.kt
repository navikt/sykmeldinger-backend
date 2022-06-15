package no.nav.syfo.arbeidsgivere.client.arbeidsforhold.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.log
import no.nav.syfo.metrics.HTTP_CLIENT_HISTOGRAM
import no.nav.syfo.tokenx.TokenXClient
import java.time.LocalDate

class ArbeidsforholdClient(
    private val httpClient: HttpClient,
    private val url: String,
    private val tokenXClient: TokenXClient,
    private val audience: String
) {

    private val arbeidsforholdPath = "$url/api/v1/arbeidstaker/arbeidsforhold"
    private val navPersonident = "Nav-Personident"
    private val ansettelsesperiodeFomQueryParam = "ansettelsesperiodeFom"
    private val ansettelsesperiodeTomQueryParam = "ansettelsesperiodeTom"
    private val sporingsinformasjon = "sporingsinformasjon"

    suspend fun getArbeidsforholdTokenX(fnr: String, ansettelsesperiodeFom: LocalDate, subjectToken: String): List<Arbeidsforhold> {
        val token = tokenXClient.getAccessToken(
            subjectToken = subjectToken,
            audience = audience
        )
        val iMorgen = LocalDate.now().plusDays(1).toString()
        val timer = HTTP_CLIENT_HISTOGRAM.labels(arbeidsforholdPath).startTimer()
        try {
            return httpClient.get(
                "$arbeidsforholdPath?" +
                    "$ansettelsesperiodeFomQueryParam=$ansettelsesperiodeFom&" +
                    "$ansettelsesperiodeTomQueryParam=$iMorgen&" +
                    "$sporingsinformasjon=false"
            ) {
                header(navPersonident, fnr)
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av arbeidsforhold (tokenX)", e)
            throw e
        } finally {
            timer.observeDuration()
        }
    }
}
