package no.nav.syfo.arbeidsgivere.client.arbeidsforhold.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsforhold
import no.nav.syfo.client.TokenXClient
import no.nav.syfo.log
import java.time.LocalDate

class ArbeidsforholdClient(
    private val httpClient: HttpClient,
    private val url: String,
    private val tokenXClient: TokenXClient,
    private val audience: String
) {

    private val arbeidsforholdPath = "$url/api/v1/arbeidstaker/arbeidsforhold"
    private val navPersonident = "Nav-Personident"
    private val navConsumerToken = "Nav-Consumer-Token"
    private val ansettelsesperiodeFomQueryParam = "ansettelsesperiodeFom"
    private val ansettelsesperiodeTomQueryParam = "ansettelsesperiodeTom"
    private val sporingsinformasjon = "sporingsinformasjon"

    suspend fun getArbeidsforhold(fnr: String, ansettelsesperiodeFom: LocalDate, token: String, stsToken: String): List<Arbeidsforhold> {
        val iMorgen = LocalDate.now().plusDays(1).toString()
        try {
            return httpClient.get(
                "$arbeidsforholdPath?" +
                    "$ansettelsesperiodeFomQueryParam=$ansettelsesperiodeFom&" +
                    "$ansettelsesperiodeTomQueryParam=$iMorgen&" +
                    "$sporingsinformasjon=false"
            ) {
                header(navPersonident, fnr)
                header(HttpHeaders.Authorization, token)
                header(navConsumerToken, "Bearer $stsToken")
            }
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av arbeidsforhold", e)
            throw e
        }
    }

    suspend fun getArbeidsforholdTokenX(fnr: String, ansettelsesperiodeFom: LocalDate, subjectToken: String): List<Arbeidsforhold> {
        val token = tokenXClient.getAccessToken(
            subjectToken = subjectToken.removePrefix("Bearer "),
            audience = audience
        )
        val iMorgen = LocalDate.now().plusDays(1).toString()
        try {
            return httpClient.get(
                "$arbeidsforholdPath?" +
                    "$ansettelsesperiodeFomQueryParam=$ansettelsesperiodeFom&" +
                    "$ansettelsesperiodeTomQueryParam=$iMorgen&" +
                    "$sporingsinformasjon=false"
            ) {
                header(navPersonident, fnr)
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av arbeidsforhold (tokenX)", e)
            throw e
        }
    }
}
