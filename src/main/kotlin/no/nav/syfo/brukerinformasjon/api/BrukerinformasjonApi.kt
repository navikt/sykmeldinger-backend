package no.nav.syfo.brukerinformasjon.api

import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.time.LocalDate
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.plugins.BrukerPrincipal
import no.nav.syfo.sykmelding.SykmeldingService
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.utils.objectMapper
import no.nav.syfo.utils.securelog
import org.koin.ktor.ext.inject

fun Route.registrerBrukerinformasjonApi() {
    val arbeidsgiverService by inject<ArbeidsgiverService>()
    val sykmeldingService by inject<SykmeldingService>()

    get("/brukerinformasjon") {
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr

        val arbeidsgivere = arbeidsgiverService.getArbeidsgivere(fnr = fnr)
        securelog.info("arbeidsgivere for $fnr, ${objectMapper.writeValueAsString(arbeidsgivere)}")
        call.respond(
            Brukerinformasjon(
                arbeidsgivere = arbeidsgivere,
            ),
        )
    }

    get("/brukerinformasjon/{sykmeldingId}") {
        val sykmeldingId = call.parameters["sykmeldingId"]!!
        val principal: BrukerPrincipal = call.authentication.principal()!!
        val fnr = principal.fnr

        val sykmelding = sykmeldingService.getSykmelding(fnr = fnr, sykmeldingid = sykmeldingId)
        if (sykmelding != null) {
            val (sykmeldingFom, sykmeldingTom) = getSykmeldingFomTom(sykmelding)
            val arbeidsgivere =
                arbeidsgiverService.getArbeidsgivereWithinSykmeldingPeriode(
                    sykmeldingFom,
                    sykmeldingTom,
                    fnr = fnr
                )
            securelog.info("arbeidsgivere for $fnr, sykmeldingId $sykmeldingId, fom: $sykmeldingFom, tom: $sykmeldingTom, ${objectMapper.writeValueAsString(arbeidsgivere)}")
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

private fun getSykmeldingFomTom(sykmelding: SykmeldingDTO): Pair<LocalDate, LocalDate> {
    val fom = sykmelding.sykmeldingsperioder.minOf { it.fom }
    val tom = sykmelding.sykmeldingsperioder.maxOf { it.tom }
    return fom to tom
}
