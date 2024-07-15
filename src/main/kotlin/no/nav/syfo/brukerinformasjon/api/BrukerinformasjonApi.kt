package no.nav.syfo.brukerinformasjon.api

import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.plugins.BrukerPrincipal
import no.nav.syfo.sykmelding.SykmeldingService
import org.koin.ktor.ext.inject

fun Route.registrerBrukerinformasjonApi() {
    val arbeidsgiverService by inject<ArbeidsgiverService>()
    val sykmeldingService by inject<SykmeldingService>()

    get("/brukerinformasjon") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr

        val arbeidsgivere = arbeidsgiverService.getArbeidsgivere(fnr = fnr)

        call.respond(
            Brukerinformasjon(
                arbeidsgivere = arbeidsgivere,
            ),
        )
    }

    get("/arbeidsgivere/{sykmeldingId}") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr

        val sykmelding = sykmeldingService.getSykmelding(fnr = fnr, sykmeldingid = sykmeldingId)
        if (sykmelding != null) {
            val arbeidsgivere =
                arbeidsgiverService.getArbeidsgivereWithinSykmeldingPeriode(sykmelding, fnr = fnr)
            call.respond(
                Brukerinformasjon(
                    arbeidsgivere = arbeidsgivere,
                ),
            )
        } else {
            val arbeidsgivere = arbeidsgiverService.getArbeidsgivere(fnr = fnr)
            call.respond(
                Brukerinformasjon(
                    arbeidsgivere = arbeidsgivere,
                ),
            )
        }
    }
}

data class Brukerinformasjon(
    val arbeidsgivere: List<Arbeidsgiverinfo>,
)
