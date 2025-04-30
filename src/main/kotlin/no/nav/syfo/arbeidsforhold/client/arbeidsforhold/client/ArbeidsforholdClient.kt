package no.nav.syfo.arbeidsforhold.client.arbeidsforhold.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.sykmeldinger.arbeidsforhold.client.arbeidsforhold.model.AaregArbeidsforhold
import org.slf4j.LoggerFactory

class ArbeidsforholdClient(
    private val httpClient: HttpClient,
    url: String,
    private val accessTokenClient: AccessTokenClient,
    private val scope: String,
) {

    companion object {
        private val log = LoggerFactory.getLogger(ArbeidsforholdClient::class.java)
    }

    private val arbeidsforholdPath = "$url/api/v2/arbeidstaker/arbeidsforhold"
    private val navPersonident = "Nav-Personident"

    suspend fun getArbeidsforhold(fnr: String): List<AaregArbeidsforhold> {
        val token = accessTokenClient.getAccessToken(scope)
        try {
            return httpClient
                .get(
                    "$arbeidsforholdPath?" +
                        "sporingsinformasjon=false&" +
                        "arbeidsforholdstatus=AKTIV,FREMTIDIG,AVSLUTTET",
                ) {
                    header(navPersonident, fnr)
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                .body()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av arbeidsforhold", e)
            throw e
        }
    }
}
