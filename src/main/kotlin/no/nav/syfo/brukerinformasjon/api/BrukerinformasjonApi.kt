package no.nav.syfo.brukerinformasjon.api

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.http.HttpHeaders
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.pdl.service.PdlPersonService
import java.util.UUID

fun Route.registrerBrukerinformasjonApi(arbeidsgiverService: ArbeidsgiverService, pdlPersonService: PdlPersonService, stsOidcClient: StsOidcClient) {
    route("api/v1") {
        get("/brukerinformasjon") {
            val principal: BrukerPrincipal = call.authentication.principal()!!
            val fnr = principal.fnr
            val token = call.request.headers[HttpHeaders.Authorization]!!

            val stsToken = stsOidcClient.oidcToken()
            val person = pdlPersonService.getPerson(
                fnr = fnr,
                userToken = token,
                callId = UUID.randomUUID().toString(),
                stsToken = stsToken.access_token
            )

            val arbeidsgivere = if (person.diskresjonskode) emptyList() else arbeidsgiverService.getArbeidsgivere(
                fnr = fnr,
                token = token,
                sykmeldingId = UUID.randomUUID().toString()
            )

            call.respond(
                Brukerinformasjon(
                    strengtFortroligAdresse = person.diskresjonskode,
                    arbeidsgivere = arbeidsgivere
                )
            )
        }
    }
}

data class Brukerinformasjon(
    val arbeidsgivere: List<Arbeidsgiverinfo>,
    val strengtFortroligAdresse: Boolean
)
