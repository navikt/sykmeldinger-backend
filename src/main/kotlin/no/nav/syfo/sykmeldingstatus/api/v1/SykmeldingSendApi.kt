package no.nav.syfo.sykmeldingstatus.api.v1

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.metrics.SENDT_AV_BRUKER_COUNTER
import no.nav.syfo.sykmeldingstatus.SykmeldingStatusService
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@KtorExperimentalAPI
fun Route.registerSykmeldingSendApi(
    sykmeldingStatusService: SykmeldingStatusService,
    arbeidsgiverService: ArbeidsgiverService
) {
    post("/api/v1/sykmeldinger/{sykmeldingid}/send") {
        val sykmeldingId = call.parameters["sykmeldingid"]!!
        val token = call.request.headers["Authorization"]!!
        val principal: JWTPrincipal = call.authentication.principal()!!
        val fnr = principal.payload.subject
        val sykmeldingSendEventUserDTO = call.receive<SykmeldingSendEventUserDTO>()

        val arbeidsgivere = arbeidsgiverService.getArbeidsgivere(
            fnr = fnr,
            token = token,
            date = LocalDate.now(),
            sykmeldingId = sykmeldingId
        )
        val arbeidsgiver = arbeidsgivere.find { it.orgnummer == sykmeldingSendEventUserDTO.orgnummer }
            ?: throw InvalidSykmeldingStatusException("Kan ikke sende sykmelding $sykmeldingId til orgnummer ${sykmeldingSendEventUserDTO.orgnummer} fordi bruker ikke har arbeidsforhold der")

        sykmeldingStatusService.registrerSendt(
            sykmeldingSendEventDTO = SykmeldingSendEventDTO(
                timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                sporsmalOgSvarListe = tilSporsmalOgSvarListe(sykmeldingSendEventUserDTO, arbeidsgiver.aktivtArbeidsforhold),
                arbeidsgiver = ArbeidsgiverStatusDTO(
                    orgnummer = arbeidsgiver.orgnummer,
                    juridiskOrgnummer = arbeidsgiver.juridiskOrgnummer,
                    orgNavn = arbeidsgiver.navn
                )
            ),
            sykmeldingId = sykmeldingId,
            source = "user",
            fnr = fnr,
            token = token,
            fromSyfoservice = false
        )

        SENDT_AV_BRUKER_COUNTER.inc()
        call.respond(HttpStatusCode.Accepted)
    }
}

fun tilSporsmalOgSvarListe(sykmeldingSendEventUserDTO: SykmeldingSendEventUserDTO, aktivtArbeidsforhold: Boolean): List<SporsmalOgSvarDTO> {
    val komplettSporsmalOgSvarListe = mutableListOf(
        SporsmalOgSvarDTO(
            tekst = "Jeg er sykmeldt fra",
            shortName = ShortNameDTO.ARBEIDSSITUASJON,
            svartype = SvartypeDTO.ARBEIDSSITUASJON,
            svar = "ARBEIDSTAKER"
        )
    )
    if (!aktivtArbeidsforhold) {
        komplettSporsmalOgSvarListe.add(
            SporsmalOgSvarDTO(
                tekst = "Skal finne ny nærmeste leder",
                shortName = ShortNameDTO.NY_NARMESTE_LEDER,
                svartype = SvartypeDTO.JA_NEI,
                svar = "NEI"
            )
        )
    } else if (sykmeldingSendEventUserDTO.beOmNyNaermesteLeder == true) {
        komplettSporsmalOgSvarListe.add(
            SporsmalOgSvarDTO(
                tekst = "Skal finne ny nærmeste leder",
                shortName = ShortNameDTO.NY_NARMESTE_LEDER,
                svartype = SvartypeDTO.JA_NEI,
                svar = "JA"
            )
        )
    }
    sykmeldingSendEventUserDTO.sporsmalOgSvarListe?.forEach { komplettSporsmalOgSvarListe.add(it) }
    return komplettSporsmalOgSvarListe
}
