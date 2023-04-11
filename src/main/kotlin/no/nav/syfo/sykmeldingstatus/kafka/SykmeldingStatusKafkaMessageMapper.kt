package no.nav.syfo.sykmeldingstatus.kafka

import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_APEN
import no.nav.syfo.model.sykmeldingstatus.STATUS_AVBRUTT
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.model.sykmeldingstatus.STATUS_UTGATT
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v2.JaEllerNei
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingUserEvent
import no.nav.syfo.sykmeldingstatus.toStatusEvent
import java.time.OffsetDateTime

fun SykmeldingUserEvent.tilSykmeldingStatusKafkaEventDTO(timestamp: OffsetDateTime, sykmeldingId: String, arbeidsgiver: Arbeidsgiverinfo?): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(
        sykmeldingId,
        timestamp,
        this.toStatusEvent().tilStatusEventDTO(),
        arbeidsgiver?.let {
            ArbeidsgiverStatusDTO(
                orgnummer = it.orgnummer,
                juridiskOrgnummer = it.juridiskOrgnummer,
                orgNavn = it.navn,
            )
        },
        toSporsmalSvarListe(arbeidsgiver, sykmeldingId),
    )
}

fun SykmeldingUserEvent.toSporsmalSvarListe(arbeidsgiver: Arbeidsgiverinfo? = null, sykmeldingId: String): List<SporsmalOgSvarDTO> {
    return listOfNotNull(
        arbeidssituasjonSporsmalBuilder(),
        fravarSporsmalBuilder(),
        periodeSporsmalBuilder(),
        riktigNarmesteLederSporsmalBuilder(arbeidsgiver, sykmeldingId),
        forsikringSporsmalBuilder(),
        egenmeldingsdagerBuilder(),
    )
}

private fun SykmeldingUserEvent.egenmeldingsdagerBuilder(): SporsmalOgSvarDTO? {
    if (egenmeldingsdager == null) return null

    return SporsmalOgSvarDTO(
        tekst = egenmeldingsdager.sporsmaltekst,
        shortName = ShortNameDTO.EGENMELDINGSDAGER,
        svartype = SvartypeDTO.DAGER,
        svar = objectMapper.writeValueAsString(egenmeldingsdager.svar),
    )
}

private fun SykmeldingUserEvent.arbeidssituasjonSporsmalBuilder(): SporsmalOgSvarDTO {
    return SporsmalOgSvarDTO(
        tekst = arbeidssituasjon.sporsmaltekst,
        shortName = ShortNameDTO.ARBEIDSSITUASJON,
        svartype = SvartypeDTO.ARBEIDSSITUASJON,
        svar = arbeidssituasjon.svar.name,
    )
}

private fun SykmeldingUserEvent.fravarSporsmalBuilder(): SporsmalOgSvarDTO? {
    if (harBruktEgenmelding != null) {
        return SporsmalOgSvarDTO(
            tekst = harBruktEgenmelding.sporsmaltekst,
            shortName = ShortNameDTO.FRAVAER,
            svartype = SvartypeDTO.JA_NEI,
            svar = harBruktEgenmelding.svar.name,
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

private fun SykmeldingUserEvent.riktigNarmesteLederSporsmalBuilder(arbeidsgiver: Arbeidsgiverinfo?, sykmeldingId: String): SporsmalOgSvarDTO? {
    if (arbeidsgiver?.aktivtArbeidsforhold == false) {
        log.info("Ber ikke om ny nærmeste leder for arbeidsforhold som ikke er aktivt: $sykmeldingId")
        return SporsmalOgSvarDTO(
            tekst = "Skal finne ny nærmeste leder",
            shortName = ShortNameDTO.NY_NARMESTE_LEDER,
            svartype = SvartypeDTO.JA_NEI,
            svar = "NEI",
        )
    }

    if (riktigNarmesteLeder != null) {
        return SporsmalOgSvarDTO(
            tekst = riktigNarmesteLeder.sporsmaltekst,
            shortName = ShortNameDTO.NY_NARMESTE_LEDER,
            svartype = SvartypeDTO.JA_NEI,
            svar = when (riktigNarmesteLeder.svar) {
                JaEllerNei.JA -> JaEllerNei.NEI
                JaEllerNei.NEI -> JaEllerNei.JA
            }.name,
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
            svar = harForsikring.svar.name,
        )
    }
    return null
}

fun SykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId: String): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(sykmeldingId, this.timestamp, this.statusEvent.tilStatusEventDTO(), null, null)
}

fun SykmeldingBekreftEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId: String): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(sykmeldingId, this.timestamp, STATUS_BEKREFTET, null, tilSporsmalOgSvarDTOListe(this.sporsmalOgSvarListe))
}

fun StatusEventDTO.tilStatusEventDTO(): String {
    return when (this) {
        StatusEventDTO.BEKREFTET -> STATUS_BEKREFTET
        StatusEventDTO.APEN -> STATUS_APEN
        StatusEventDTO.SENDT -> STATUS_SENDT
        StatusEventDTO.AVBRUTT -> STATUS_AVBRUTT
        StatusEventDTO.UTGATT -> STATUS_UTGATT
    }
}

fun no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO.tilArbeidsgiverStatusDTO(): ArbeidsgiverStatusDTO {
    return ArbeidsgiverStatusDTO(orgnummer = this.orgnummer, juridiskOrgnummer = this.juridiskOrgnummer, orgNavn = this.orgNavn)
}

fun tilSporsmalOgSvarDTOListe(sporsmalListe: List<no.nav.syfo.sykmeldingstatus.api.v1.SporsmalOgSvarDTO>?): List<SporsmalOgSvarDTO>? {
    return if (sporsmalListe.isNullOrEmpty()) {
        null
    } else {
        sporsmalListe.map { tilSporsmalOgSvarDTO(it) }
    }
}

fun tilSporsmalOgSvarDTO(sporsmalOgSvar: no.nav.syfo.sykmeldingstatus.api.v1.SporsmalOgSvarDTO): SporsmalOgSvarDTO =
    SporsmalOgSvarDTO(tekst = sporsmalOgSvar.tekst, shortName = sporsmalOgSvar.shortName.tilShortNameDTO(), svartype = sporsmalOgSvar.svartype.tilSvartypeDTO(), svar = sporsmalOgSvar.svar)

fun no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.tilShortNameDTO(): ShortNameDTO {
    return when (this) {
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.ARBEIDSSITUASJON -> ShortNameDTO.ARBEIDSSITUASJON
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.PERIODE -> ShortNameDTO.PERIODE
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.FRAVAER -> ShortNameDTO.FRAVAER
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.FORSIKRING -> ShortNameDTO.FORSIKRING
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.NY_NARMESTE_LEDER -> ShortNameDTO.NY_NARMESTE_LEDER
    }
}

fun no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO.tilSvartypeDTO(): SvartypeDTO {
    return when (this) {
        no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO.ARBEIDSSITUASJON -> SvartypeDTO.ARBEIDSSITUASJON
        no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO.PERIODER -> SvartypeDTO.PERIODER
        no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO.JA_NEI -> SvartypeDTO.JA_NEI
    }
}
