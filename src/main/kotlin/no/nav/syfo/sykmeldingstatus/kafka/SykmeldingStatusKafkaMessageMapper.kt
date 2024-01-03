package no.nav.syfo.sykmeldingstatus.kafka

import java.time.OffsetDateTime
import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon
import no.nav.syfo.sykmeldingstatus.api.v2.Blad
import no.nav.syfo.sykmeldingstatus.api.v2.JaEllerNei
import no.nav.syfo.sykmeldingstatus.api.v2.LottOgHyre
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingFormResponse
import no.nav.syfo.sykmeldingstatus.kafka.model.ArbeidsgiverStatusKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.FiskereSvarKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.KomplettInnsendtSkjemaSvar
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_APEN
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_AVBRUTT
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_BEKREFTET
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_SENDT
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_UTGATT
import no.nav.syfo.sykmeldingstatus.kafka.model.ShortNameKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SporsmalOgSvarKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SvartypeKafkaDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.TidligereArbeidsgiverKafkaDTO
import no.nav.syfo.sykmeldingstatus.toStatusEvent

fun SykmeldingFormResponse.tilSykmeldingStatusKafkaEventDTO(
    timestamp: OffsetDateTime,
    sykmeldingId: String,
    arbeidsgiver: Arbeidsgiverinfo?,
    tidligereArbeidsgiver: TidligereArbeidsgiverDTO?,
): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(
        sykmeldingId = sykmeldingId,
        timestamp = timestamp,
        statusEvent = this.toStatusEvent().tilStatusEventDTO(),
        arbeidsgiver =
            arbeidsgiver?.let {
                ArbeidsgiverStatusKafkaDTO(
                    orgnummer = it.orgnummer,
                    juridiskOrgnummer = it.juridiskOrgnummer,
                    orgNavn = it.navn,
                )
            },
        sporsmals = toSporsmalSvarListe(arbeidsgiver, sykmeldingId),
        brukerSvar = toKomplettInnsendtSkjema(),
        tidligereArbeidsgiver =
            tidligereArbeidsgiver?.let {
                TidligereArbeidsgiverKafkaDTO(
                    orgNavn = it.orgNavn,
                    orgnummer = it.orgnummer,
                    sykmeldingsId = it.sykmeldingsId,
                )
            },
    )
}

private fun SykmeldingFormResponse.toKomplettInnsendtSkjema() =
    KomplettInnsendtSkjemaSvar(
        erOpplysningeneRiktige = erOpplysningeneRiktige,
        uriktigeOpplysninger = uriktigeOpplysninger,
        arbeidssituasjon = arbeidssituasjon,
        arbeidsgiverOrgnummer = arbeidsgiverOrgnummer,
        riktigNarmesteLeder = riktigNarmesteLeder,
        harBruktEgenmelding = harBruktEgenmelding,
        egenmeldingsperioder = egenmeldingsperioder,
        harForsikring = harForsikring,
        egenmeldingsdager = egenmeldingsdager,
        harBruktEgenmeldingsdager = harBruktEgenmeldingsdager,
        fisker =
            fisker?.let {
                FiskereSvarKafkaDTO(
                    blad = it.blad,
                    lottOgHyre = it.lottOgHyre,
                )
            },
    )

fun SykmeldingFormResponse.toSporsmalSvarListe(
    arbeidsgiver: Arbeidsgiverinfo? = null,
    sykmeldingId: String
): List<SporsmalOgSvarKafkaDTO> {
    return listOfNotNull(
        arbeidssituasjonSporsmalBuilder(),
        fravarSporsmalBuilder(),
        periodeSporsmalBuilder(),
        riktigNarmesteLederSporsmalBuilder(arbeidsgiver, sykmeldingId),
        forsikringSporsmalBuilder(),
        egenmeldingsdagerBuilder(),
    )
}

private fun SykmeldingFormResponse.egenmeldingsdagerBuilder(): SporsmalOgSvarKafkaDTO? {
    if (egenmeldingsdager == null) return null

    return SporsmalOgSvarKafkaDTO(
        tekst = egenmeldingsdager.sporsmaltekst,
        shortName = ShortNameKafkaDTO.EGENMELDINGSDAGER,
        svartype = SvartypeKafkaDTO.DAGER,
        svar = objectMapper.writeValueAsString(egenmeldingsdager.svar),
    )
}

private fun SykmeldingFormResponse.arbeidssituasjonSporsmalBuilder(): SporsmalOgSvarKafkaDTO {
    // In the old sporsmal and svar list, fiskere should be mapped to ARBEIDSTAKER or
    // NAERINGSDRIVENDE, dependening on whether or not they are working on lott or hyre.
    val normalisertSituasjon: Arbeidssituasjon =
        when (arbeidssituasjon.svar) {
            Arbeidssituasjon.FISKER -> {
                val isHyre = fisker?.lottOgHyre?.svar == LottOgHyre.HYRE

                if (isHyre) Arbeidssituasjon.ARBEIDSTAKER else Arbeidssituasjon.NAERINGSDRIVENDE
            }
            else -> arbeidssituasjon.svar
        }

    return SporsmalOgSvarKafkaDTO(
        tekst = arbeidssituasjon.sporsmaltekst,
        shortName = ShortNameKafkaDTO.ARBEIDSSITUASJON,
        svartype = SvartypeKafkaDTO.ARBEIDSSITUASJON,
        svar = normalisertSituasjon.name,
    )
}

private fun SykmeldingFormResponse.fravarSporsmalBuilder(): SporsmalOgSvarKafkaDTO? {
    if (harBruktEgenmelding != null) {
        return SporsmalOgSvarKafkaDTO(
            tekst = harBruktEgenmelding.sporsmaltekst,
            shortName = ShortNameKafkaDTO.FRAVAER,
            svartype = SvartypeKafkaDTO.JA_NEI,
            svar = harBruktEgenmelding.svar.name,
        )
    }
    return null
}

private fun SykmeldingFormResponse.periodeSporsmalBuilder(): SporsmalOgSvarKafkaDTO? {
    if (egenmeldingsperioder != null) {
        return SporsmalOgSvarKafkaDTO(
            tekst = egenmeldingsperioder.sporsmaltekst,
            shortName = ShortNameKafkaDTO.PERIODE,
            svartype = SvartypeKafkaDTO.PERIODER,
            svar = objectMapper.writeValueAsString(egenmeldingsperioder.svar),
        )
    }
    return null
}

private fun SykmeldingFormResponse.riktigNarmesteLederSporsmalBuilder(
    arbeidsgiver: Arbeidsgiverinfo?,
    sykmeldingId: String
): SporsmalOgSvarKafkaDTO? {
    if (arbeidsgiver?.aktivtArbeidsforhold == false) {
        log.info(
            "Ber ikke om ny nærmeste leder for arbeidsforhold som ikke er aktivt: $sykmeldingId",
        )
        return SporsmalOgSvarKafkaDTO(
            tekst = "Skal finne ny nærmeste leder",
            shortName = ShortNameKafkaDTO.NY_NARMESTE_LEDER,
            svartype = SvartypeKafkaDTO.JA_NEI,
            svar = "NEI",
        )
    }

    if (riktigNarmesteLeder != null) {
        return SporsmalOgSvarKafkaDTO(
            tekst = riktigNarmesteLeder.sporsmaltekst,
            shortName = ShortNameKafkaDTO.NY_NARMESTE_LEDER,
            svartype = SvartypeKafkaDTO.JA_NEI,
            svar =
                when (riktigNarmesteLeder.svar) {
                    JaEllerNei.JA -> JaEllerNei.NEI
                    JaEllerNei.NEI -> JaEllerNei.JA
                }.name,
        )
    }
    return null
}

private fun SykmeldingFormResponse.forsikringSporsmalBuilder(): SporsmalOgSvarKafkaDTO? {
    if (harForsikring != null) {
        return SporsmalOgSvarKafkaDTO(
            tekst = harForsikring.sporsmaltekst,
            shortName = ShortNameKafkaDTO.FORSIKRING,
            svartype = SvartypeKafkaDTO.JA_NEI,
            svar = harForsikring.svar.name,
        )
    }

    if (fisker?.blad?.svar == Blad.B && fisker.lottOgHyre.svar != LottOgHyre.HYRE) {
        return SporsmalOgSvarKafkaDTO(
            tekst = "Har du forsikring som gjelder for de første 16 dagene av sykefraværet?",
            shortName = ShortNameKafkaDTO.FORSIKRING,
            svartype = SvartypeKafkaDTO.JA_NEI,
            svar = "JA",
        )
    }

    return null
}

fun SykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(
    sykmeldingId: String
): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(
        sykmeldingId,
        this.timestamp,
        this.statusEvent.tilStatusEventDTO(),
        null,
        null,
    )
}

fun SykmeldingBekreftEventDTO.tilSykmeldingStatusKafkaEventDTO(
    sykmeldingId: String
): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(
        sykmeldingId,
        this.timestamp,
        STATUS_BEKREFTET,
        null,
        tilSporsmalOgSvarDTOListe(this.sporsmalOgSvarListe),
    )
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

fun tilSporsmalOgSvarDTOListe(
    sporsmalListe: List<no.nav.syfo.sykmeldingstatus.api.v1.SporsmalOgSvarDTO>?
): List<SporsmalOgSvarKafkaDTO>? {
    return if (sporsmalListe.isNullOrEmpty()) {
        null
    } else {
        sporsmalListe.map { tilSporsmalOgSvarDTO(it) }
    }
}

fun tilSporsmalOgSvarDTO(
    sporsmalOgSvar: no.nav.syfo.sykmeldingstatus.api.v1.SporsmalOgSvarDTO
): SporsmalOgSvarKafkaDTO =
    SporsmalOgSvarKafkaDTO(
        tekst = sporsmalOgSvar.tekst,
        shortName = sporsmalOgSvar.shortName.tilShortNameDTO(),
        svartype = sporsmalOgSvar.svartype.tilSvartypeDTO(),
        svar = sporsmalOgSvar.svar,
    )

fun no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.tilShortNameDTO(): ShortNameKafkaDTO {
    return when (this) {
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.ARBEIDSSITUASJON ->
            ShortNameKafkaDTO.ARBEIDSSITUASJON
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.PERIODE -> ShortNameKafkaDTO.PERIODE
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.FRAVAER -> ShortNameKafkaDTO.FRAVAER
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.FORSIKRING -> ShortNameKafkaDTO.FORSIKRING
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.NY_NARMESTE_LEDER ->
            ShortNameKafkaDTO.NY_NARMESTE_LEDER
    }
}

fun no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO.tilSvartypeDTO(): SvartypeKafkaDTO {
    return when (this) {
        no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO.ARBEIDSSITUASJON ->
            SvartypeKafkaDTO.ARBEIDSSITUASJON
        no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO.PERIODER -> SvartypeKafkaDTO.PERIODER
        no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO.JA_NEI -> SvartypeKafkaDTO.JA_NEI
    }
}
