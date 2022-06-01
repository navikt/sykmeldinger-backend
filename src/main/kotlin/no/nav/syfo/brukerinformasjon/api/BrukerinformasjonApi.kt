package no.nav.syfo.brukerinformasjon.api

import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.syfo.application.BrukerPrincipal
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.pdl.service.PdlPersonService
import java.util.UUID

fun Route.registrerBrukerinformasjonApi(arbeidsgiverService: ArbeidsgiverService, pdlPersonService: PdlPersonService) {
    get("/brukerinformasjon") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        val token = principal.token

        val person = pdlPersonService.getPerson(
            fnr = fnr,
            userToken = token,
            callId = UUID.randomUUID().toString()
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

fun Route.registrerBrukerinformasjonApiV2(arbeidsgiverService: ArbeidsgiverService, pdlPersonService: PdlPersonService) {
    get("/brukerinformasjon") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr
        val token = principal.token

        val person = pdlPersonService.getPerson(
            fnr = fnr,
            userToken = token,
            callId = UUID.randomUUID().toString()
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

data class Brukerinformasjon(
    val arbeidsgivere: List<Arbeidsgiverinfo>,
    val strengtFortroligAdresse: Boolean
)
