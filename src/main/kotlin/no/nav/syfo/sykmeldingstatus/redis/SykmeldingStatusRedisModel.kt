package no.nav.syfo.sykmeldingstatus.redis

import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingUserEvent
import no.nav.syfo.sykmeldingstatus.toStatusEvent
import java.time.OffsetDateTime

data class SykmeldingStatusRedisModel(
    val timestamp: OffsetDateTime,
    val statusEvent: StatusEventDTO,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmals: List<SporsmalOgSvarDTO>?,
    val erAvvist: Boolean? = null,
    val erEgenmeldt: Boolean? = null
)

fun SykmeldingUserEvent.tilSykmeldingStatusRedisModel(timestamp: OffsetDateTime, arbeidsgiver: Arbeidsgiverinfo?): SykmeldingStatusRedisModel {
    return SykmeldingStatusRedisModel(timestamp, this.toStatusEvent(), arbeidsgiver?.let { ArbeidsgiverStatusDTO(it.orgnummer, it.juridiskOrgnummer, it.navn) }, toSporsmalSvarListe())
}

fun SykmeldingUserEvent.toSporsmalSvarListe(): List<SporsmalOgSvarDTO> {
    return listOfNotNull(
            arbeidssituasjonSporsmalBuilder(),
            fravarSporsmalBuilder(),
            periodeSporsmalBuilder(),
            nyNarmesteLederSporsmalBuilder(),
            forsikringSporsmalBuilder(),
    )
}

private fun SykmeldingUserEvent.arbeidssituasjonSporsmalBuilder(): SporsmalOgSvarDTO {
    return SporsmalOgSvarDTO(
            tekst = arbeidssituasjon.sporsmaltekst,
            shortName = ShortNameDTO.ARBEIDSSITUASJON,
            svartype = SvartypeDTO.ARBEIDSSITUASJON,
            svar = objectMapper.writeValueAsString(arbeidssituasjon.svar),
    )
}

private fun SykmeldingUserEvent.fravarSporsmalBuilder(): SporsmalOgSvarDTO? {
    if (harBruktEgenmelding != null) {
        return SporsmalOgSvarDTO(
                tekst = harBruktEgenmelding.sporsmaltekst,
                shortName = ShortNameDTO.FRAVAER,
                svartype = SvartypeDTO.JA_NEI,
                svar = objectMapper.writeValueAsString(harBruktEgenmelding.svar),
        )
    }
    return null
}

private fun SykmeldingUserEvent.periodeSporsmalBuilder(): SporsmalOgSvarDTO? {
    if (egenmeldingsperioder != null) {
        return SporsmalOgSvarDTO(
                tekst = egenmeldingsperioder.sporsmaltekst,
                shortName = ShortNameDTO.PERIODE,
                svartype = SvartypeDTO.PERIODER,
                svar = objectMapper.writeValueAsString(egenmeldingsperioder.svar),
        )
    }
    return null
}

private fun SykmeldingUserEvent.nyNarmesteLederSporsmalBuilder(): SporsmalOgSvarDTO? {
    if (nyNarmesteLeder != null) {
        return SporsmalOgSvarDTO(
                tekst = nyNarmesteLeder.sporsmaltekst,
                shortName = ShortNameDTO.NY_NARMESTE_LEDER,
                svartype = SvartypeDTO.JA_NEI,
                svar = objectMapper.writeValueAsString(nyNarmesteLeder.svar),
        )
    }
    return null
}

private fun SykmeldingUserEvent.forsikringSporsmalBuilder(): SporsmalOgSvarDTO? {
    if (harForsikring != null) {
        return SporsmalOgSvarDTO(
                tekst = harForsikring.sporsmaltekst,
                shortName = ShortNameDTO.FORSIKRING,
                svartype = SvartypeDTO.JA_NEI,
                svar = objectMapper.writeValueAsString(harForsikring.svar),
        )
    }
    return null
}

fun SykmeldingBekreftEventDTO.toSykmeldingStatusRedisModel(): SykmeldingStatusRedisModel {
    return SykmeldingStatusRedisModel(timestamp, StatusEventDTO.BEKREFTET, null, sporsmalOgSvarListe, erAvvist, erEgenmeldt)
}

fun SykmeldingSendEventDTO.toSykmeldingStatusRedisModel(): SykmeldingStatusRedisModel {
    return SykmeldingStatusRedisModel(timestamp, StatusEventDTO.SENDT, arbeidsgiver, sporsmalOgSvarListe, erAvvist, erEgenmeldt)
}

fun SykmeldingStatusEventDTO.toSykmeldingRedisModel(): SykmeldingStatusRedisModel {
    return SykmeldingStatusRedisModel(timestamp, statusEvent, null, null, erAvvist, erEgenmeldt)
}
