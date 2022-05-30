package no.nav.syfo.brukerinformasjon.api

import io.ktor.http.HttpHeaders
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
        val token = call.request.headers[HttpHeaders.Authorization]!!

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
        val token = call.request.headers[HttpHeaders.Authorization]!!
        val tokenUtenPrefiks = token.removePrefix("Bearer ")

        val person = pdlPersonService.getPerson(
            fnr = fnr,
            userToken = tokenUtenPrefiks,
            callId = UUID.randomUUID().toString()
        )

        val arbeidsgivere = if (person.diskresjonskode) emptyList() else arbeidsgiverService.getArbeidsgivere(
            fnr = fnr,
            token = tokenUtenPrefiks,
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
