package no.nav.syfo.brukerinformasjon.api

import io.ktor.application.call
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.principal
import io.ktor.http.HttpHeaders
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model.Arbeidsgiver
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.pdl.service.PdlPersonService
import java.time.LocalDate
import java.util.UUID

@KtorExperimentalAPI
fun Route.registrerBrukerinformasjonApi(arbeidsgiverService: ArbeidsgiverService, pdlPersonService: PdlPersonService, stsOidcClient: StsOidcClient) {
    route("api/v1") {
        get("/brukerinformasjon") {
            val principal = call.principal<JWTPrincipal>()!!
            val fnr = principal.payload.subject
            val token = call.request.headers[HttpHeaders.Authorization]!!

            val stsToken = stsOidcClient.oidcToken()
            val person = pdlPersonService.getPerson(
                    fnr = fnr,
                    userToken = token,
                    callId = UUID.randomUUID().toString(),
                    stsToken = stsToken.access_token
            )

            val arbeidsgivere = arbeidsgiverService.getArbeidsgivere(
                    fnr = fnr,
                    token = token,
                    date = LocalDate.now(),
                    sykmeldingId = UUID.randomUUID().toString()
            )

            call.respond(
                    BrukerinformasjonV2(
                            arbeidsgivere = arbeidsgivere,
                            strengtFortroligAdresse = person.diskresjonskode
                    )
            )
        }
        get("/syforest/brukerinformasjon") {
            val principal = call.principal<JWTPrincipal>()!!
            val fnr = principal.payload.subject
            val token = call.request.headers[HttpHeaders.Authorization]!!

            val stsToken = stsOidcClient.oidcToken()
            val person = pdlPersonService.getPerson(
                    fnr = fnr,
                    userToken = token,
                    callId = UUID.randomUUID().toString(),
                    stsToken = stsToken.access_token
            )

            call.respond(
                    Brukerinformasjon(strengtFortroligAdresse = person.diskresjonskode)
            )
        }
    }
}

data class Brukerinformasjon(
        val strengtFortroligAdresse: Boolean
)

data class BrukerinformasjonV2(
        val arbeidsgivere: List<Arbeidsgiverinfo>,
        val strengtFortroligAdresse: Boolean
)