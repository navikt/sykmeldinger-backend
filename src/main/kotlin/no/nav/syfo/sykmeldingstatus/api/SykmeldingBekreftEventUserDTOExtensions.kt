package no.nav.syfo.sykmeldingstatus.api
import no.nav.syfo.objectMapper

fun SykmeldingBekreftEventUserDTOv2.validate() {
    if (erOpplysnigeneRiktige.svar == JaEllerNei.NEI) {
        requireNotNull(uriktigeOpplysninger)
    }
    if (arbeidssituasjon.svar == ArbeidssituasjonDTO.ARBEIDSTAKER) {
        requireNotNull(arbeidsgiverOrgnummer)
        requireNotNull(nyNarmesteLeder)
    } else {
        require(arbeidsgiverOrgnummer == null)
        require(nyNarmesteLeder == null)
    }
    if (arbeidssituasjon.svar == ArbeidssituasjonDTO.ARBEIDSTAKER || arbeidssituasjon.svar == ArbeidssituasjonDTO.FRILANSER || arbeidssituasjon.svar == ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE) {
        if (harBruktEgenmelding != null) {
            requireNotNull(harForsikring)
            if (harBruktEgenmelding.svar == JaEllerNei.JA) {
                requireNotNull(egenmeldingsperioder)
            }
        }
        if (harForsikring != null) {
            requireNotNull(harBruktEgenmelding)
        }
    } else {
        require(harBruktEgenmelding == null)
        require(egenmeldingsperioder == null)
        require(harForsikring == null)
    }
}

fun SykmeldingBekreftEventUserDTOv2.toSporsmalSvarListe(): List<SporsmalOgSvarDTO> {
    return listOfNotNull(
        arbeidssituasjonSporsmalBuilder(),
        fravarSporsmalBuilder(),
        periodeSporsmalBuilder(),
        nyNarmesteLederSporsmalBuilder(),
        forsikringSporsmalBuilder(),
    )
}

private fun SykmeldingBekreftEventUserDTOv2.arbeidssituasjonSporsmalBuilder(): SporsmalOgSvarDTO {
    return SporsmalOgSvarDTO(
        tekst = arbeidssituasjon.sporsmaltekst,
        shortName = ShortNameDTO.ARBEIDSSITUASJON,
        svartype = SvartypeDTO.ARBEIDSSITUASJON,
        svar = objectMapper.writeValueAsString(arbeidssituasjon.svar),
    )
}

private fun SykmeldingBekreftEventUserDTOv2.fravarSporsmalBuilder(): SporsmalOgSvarDTO? {
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

private fun SykmeldingBekreftEventUserDTOv2.periodeSporsmalBuilder(): SporsmalOgSvarDTO? {
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

private fun SykmeldingBekreftEventUserDTOv2.nyNarmesteLederSporsmalBuilder(): SporsmalOgSvarDTO? {
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

private fun SykmeldingBekreftEventUserDTOv2.forsikringSporsmalBuilder(): SporsmalOgSvarDTO? {
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
