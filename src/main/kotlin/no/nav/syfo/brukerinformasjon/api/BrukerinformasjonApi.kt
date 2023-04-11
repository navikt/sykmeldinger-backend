package no.nav.syfo.brukerinformasjon.api

import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService

fun Route.registrerBrukerinformasjonApi(arbeidsgiverService: ArbeidsgiverService) {
    get("/brukerinformasjon") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr

        val arbeidsgivere = arbeidsgiverService.getArbeidsgivere(fnr = fnr)

        call.respond(
            Brukerinformasjon(
                strengtFortroligAdresse = false,
                arbeidsgivere = arbeidsgivere,
            ),
        )
    }
}

data class Brukerinformasjon(
    val arbeidsgivere: List<Arbeidsgiverinfo>,
    val strengtFortroligAdresse: Boolean,
)
