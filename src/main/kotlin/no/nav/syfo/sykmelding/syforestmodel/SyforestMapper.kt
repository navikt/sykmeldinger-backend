package no.nav.syfo.sykmelding.syforestmodel

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.sykmelding.model.BehandlerDTO
import no.nav.syfo.sykmelding.model.DiagnoseDTO
import no.nav.syfo.sykmelding.model.KontaktMedPasientDTO
import no.nav.syfo.sykmelding.model.MedisinskVurderingDTO
import no.nav.syfo.sykmelding.model.MeldingTilNavDTO
import no.nav.syfo.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.model.PrognoseDTO
import no.nav.syfo.sykmelding.model.ShortNameDTO
import no.nav.syfo.sykmelding.model.SporsmalSvarDTO
import no.nav.syfo.sykmelding.model.SvarRestriksjonDTO
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverStatusDTO

fun pdlPersonTilPasient(fnr: String, pdlPerson: PdlPerson): Pasient {
    return Pasient(
        fnr = fnr,
        fornavn = pdlPerson.navn.fornavn,
        mellomnavn = pdlPerson.navn.mellomnavn,
        etternavn = pdlPerson.navn.etternavn
    )
}

fun tilSyforestSykmelding(sykmeldingDTO: SykmeldingDTO, pasient: Pasient): SyforestSykmelding {
    val arbeidsgiverNavnHvisSendt = sykmeldingDTO.sykmeldingStatus.arbeidsgiver?.orgNavn

    return SyforestSykmelding(
        id = sykmeldingDTO.id,
        startLegemeldtFravaer = sykmeldingDTO.syketilfelleStartDato,
        skalViseSkravertFelt = !sykmeldingDTO.skjermesForPasient,
        identdato = sykmeldingDTO.syketilfelleStartDato,
        status = tilStatus(sykmeldingDTO.sykmeldingStatus),
        naermesteLederStatus = null, // brukes ikke av frontend
        erEgenmeldt = sykmeldingDTO.egenmeldt ?: false,
        erPapirsykmelding = sykmeldingDTO.papirsykmelding ?: false,
        innsendtArbeidsgivernavn = arbeidsgiverNavnHvisSendt,
        valgtArbeidssituasjon = finnArbeidssituasjon(sykmeldingDTO.sykmeldingStatus),
        mottakendeArbeidsgiver = tilMottakendeArbeidsgiver(sykmeldingDTO.sykmeldingStatus.arbeidsgiver),
        orgnummer = sykmeldingDTO.sykmeldingStatus.arbeidsgiver?.orgnummer,
        sendtdato = finnSendtDato(sykmeldingDTO.sykmeldingStatus),
        sporsmal = tilSporsmal(sykmeldingDTO.sykmeldingStatus),
        pasient = pasient,
        arbeidsgiver = sykmeldingDTO.arbeidsgiver?.navn,
        stillingsprosent = sykmeldingDTO.arbeidsgiver?.stillingsprosent,
        diagnose = tilDiagnoseinfo(sykmeldingDTO.skjermesForPasient, sykmeldingDTO.medisinskVurdering),
        mulighetForArbeid = tilMulighetForArbeid(sykmeldingDTO.sykmeldingsperioder, sykmeldingDTO.harRedusertArbeidsgiverperiode),
        friskmelding = if (sykmeldingDTO.prognose != null) { tilFriskmelding(sykmeldingDTO.prognose) } else { Friskmelding() },
        utdypendeOpplysninger = tilUtdypendeOpplysninger(sykmeldingDTO.utdypendeOpplysninger),
        arbeidsevne = Arbeidsevne(tilretteleggingArbeidsplass = sykmeldingDTO.tiltakArbeidsplassen, tiltakNAV = sykmeldingDTO.tiltakNAV, tiltakAndre = sykmeldingDTO.andreTiltak),
        meldingTilNav = if (sykmeldingDTO.meldingTilNAV != null) { tilMeldingTilNav((sykmeldingDTO.meldingTilNAV)) } else { MeldingTilNav() },
        innspillTilArbeidsgiver = sykmeldingDTO.meldingTilArbeidsgiver,
        tilbakedatering = tilTilbakedatering(sykmeldingDTO.kontaktMedPasient),
        bekreftelse = tilBekreftelse(sykmeldingDTO.behandler, sykmeldingDTO.behandletTidspunkt)
    )
}

fun tilStatus(sykmeldingStatusDTO: SykmeldingStatusDTO): String =
    if (sykmeldingStatusDTO.statusEvent == "APEN") {
        "NY"
    } else {
        sykmeldingStatusDTO.statusEvent
    }

fun finnArbeidssituasjon(sykmeldingStatusDTO: SykmeldingStatusDTO): String? {
    // for sendte sykmeldinger skal denne være null
    if (sykmeldingStatusDTO.statusEvent == "BEKREFTET") {
        return sykmeldingStatusDTO.sporsmalOgSvarListe.find { it.shortName == ShortNameDTO.ARBEIDSSITUASJON }?.svar?.svar
    }
    return null
}

fun finnSendtDato(sykmeldingStatusDTO: SykmeldingStatusDTO): LocalDateTime? {
    if (sykmeldingStatusDTO.statusEvent == "SENDT" || sykmeldingStatusDTO.statusEvent == "BEKREFTET") {
        return sykmeldingStatusDTO.timestamp.atZoneSameInstant(ZoneId.of("Europe/Oslo")).toLocalDateTime().withNano(0)
    }
    return null
}

fun tilMottakendeArbeidsgiver(arbeidsgiverStatusDTO: ArbeidsgiverStatusDTO?): MottakendeArbeidsgiver? {
    if (arbeidsgiverStatusDTO != null) {
        return MottakendeArbeidsgiver(
            navn = arbeidsgiverStatusDTO.orgNavn,
            virksomhetsnummer = arbeidsgiverStatusDTO.orgnummer,
            juridiskOrgnummer = arbeidsgiverStatusDTO.juridiskOrgnummer
        )
    }
    return null
}

fun tilSporsmal(sykmeldingStatusDTO: SykmeldingStatusDTO): Skjemasporsmal? {
    if (sykmeldingStatusDTO.statusEvent == "SENDT" || sykmeldingStatusDTO.statusEvent == "BEKREFTET") {
        val arbeidssituasjon = sykmeldingStatusDTO.sporsmalOgSvarListe.find { it.shortName == ShortNameDTO.ARBEIDSSITUASJON }?.svar?.svar
        val harForsikring = jaNeiTilBoolean(sykmeldingStatusDTO.sporsmalOgSvarListe.find { it.shortName == ShortNameDTO.FORSIKRING }?.svar?.svar)
        val harFravaer = jaNeiTilBoolean(sykmeldingStatusDTO.sporsmalOgSvarListe.find { it.shortName == ShortNameDTO.FRAVAER }?.svar?.svar)
        val fravaersperioder = tilFravaersperioder(sykmeldingStatusDTO.sporsmalOgSvarListe.find { it.shortName == ShortNameDTO.PERIODE }?.svar?.svar)
        return Skjemasporsmal(
            arbeidssituasjon = arbeidssituasjon,
            harForsikring = harForsikring,
            harAnnetFravaer = harFravaer,
            fravaersperioder = fravaersperioder
        )
    }
    return null
}

fun jaNeiTilBoolean(jaEllerNei: String?): Boolean? {
    return when (jaEllerNei) {
        "Ja" -> true
        "Nei" -> false
        else -> null
    }
}

fun tilFravaersperioder(perioder: String?): List<Datospenn> {
    if (perioder == null) {
        return emptyList()
    }
    return objectMapper.readValue<List<Datospenn>>(perioder)
}

fun tilDiagnoseinfo(skalSkjermesForPasient: Boolean, medisinskVurdering: MedisinskVurderingDTO?): Diagnoseinfo {
    if (skalSkjermesForPasient) {
        return Diagnoseinfo()
    }
    return Diagnoseinfo(
        hoveddiagnose = if (medisinskVurdering?.hovedDiagnose != null) tilDiagnose(medisinskVurdering.hovedDiagnose) else null,
        bidiagnoser = if (medisinskVurdering?.biDiagnoser != null) { medisinskVurdering.biDiagnoser.map { tilDiagnose(it) } } else emptyList(),
        fravaersgrunnLovfestet = medisinskVurdering?.annenFraversArsak?.grunn?.firstOrNull()?.name,
        fravaerBeskrivelse = medisinskVurdering?.annenFraversArsak?.beskrivelse,
        svangerskap = medisinskVurdering?.svangerskap,
        yrkesskade = medisinskVurdering?.yrkesskade,
        yrkesskadeDato = medisinskVurdering?.yrkesskadeDato
    )
}

fun tilDiagnose(diagnoseDTO: DiagnoseDTO): Diagnose {
    return Diagnose(diagnose = diagnoseDTO.tekst, diagnosekode = diagnoseDTO.kode, diagnosesystem = diagnoseDTO.system)
}

fun tilMulighetForArbeid(periodeliste: List<SykmeldingsperiodeDTO>, harRedusertArbeidsgiverperiode: Boolean?): MulighetForArbeid {
    return MulighetForArbeid(
        perioder = periodeliste.map { tilPeriode(it, harRedusertArbeidsgiverperiode) },
        aktivitetIkkeMulig433 = tilAktivitetIkkeMulig433(periodeliste),
        aktivitetIkkeMulig434 = tilAktivitetIkkeMulig434(periodeliste),
        aarsakAktivitetIkkeMulig433 = tilAarsakAktivitetIkkeMulig433(periodeliste),
        aarsakAktivitetIkkeMulig434 = tilAarsakAktivitetIkkeMulig434(periodeliste)
    )
}

fun tilPeriode(sykmeldingsperiodeDTO: SykmeldingsperiodeDTO, harRedusertArbeidsgiverperiode: Boolean?): Periode {
    return Periode(
        fom = sykmeldingsperiodeDTO.fom,
        tom = sykmeldingsperiodeDTO.tom,
        grad = sykmeldingsperiodeDTO.gradert?.grad ?: 100,
        behandlingsdager = sykmeldingsperiodeDTO.behandlingsdager,
        reisetilskudd = if (sykmeldingsperiodeDTO.reisetilskudd) { true } else { null },
        avventende = if (sykmeldingsperiodeDTO.type == PeriodetypeDTO.AVVENTENDE) { sykmeldingsperiodeDTO.innspillTilArbeidsgiver } else { null },
        redusertVenteperiode = if (harRedusertArbeidsgiverperiode == true) { true } else { null }
    )
}

fun tilAktivitetIkkeMulig433(periodeliste: List<SykmeldingsperiodeDTO>): List<String> {
    return periodeliste.flatMap { sykmeldingsperiodeDTO -> sykmeldingsperiodeDTO.aktivitetIkkeMulig?.medisinskArsak?.arsak?.map { it.text }.orEmpty() }
}

fun tilAktivitetIkkeMulig434(periodeliste: List<SykmeldingsperiodeDTO>): List<String> {
    return periodeliste.flatMap { sykmeldingsperiodeDTO -> sykmeldingsperiodeDTO.aktivitetIkkeMulig?.arbeidsrelatertArsak?.arsak?.map { it.text }.orEmpty() }
}

fun tilAarsakAktivitetIkkeMulig433(periodeliste: List<SykmeldingsperiodeDTO>): String? {
    return periodeliste.mapNotNull { sykmeldingsperiodeDTO -> sykmeldingsperiodeDTO.aktivitetIkkeMulig?.medisinskArsak?.beskrivelse }.firstOrNull()
}

fun tilAarsakAktivitetIkkeMulig434(periodeliste: List<SykmeldingsperiodeDTO>): String? {
    return periodeliste.mapNotNull { sykmeldingsperiodeDTO -> sykmeldingsperiodeDTO.aktivitetIkkeMulig?.arbeidsrelatertArsak?.beskrivelse }.firstOrNull()
}

fun tilFriskmelding(prognoseDTO: PrognoseDTO): Friskmelding {
    return Friskmelding(
        arbeidsfoerEtterPerioden = prognoseDTO.arbeidsforEtterPeriode,
        hensynPaaArbeidsplassen = prognoseDTO.hensynArbeidsplassen,
        antarReturSammeArbeidsgiver = prognoseDTO.erIArbeid?.egetArbeidPaSikt ?: false,
        antattDatoReturSammeArbeidsgiver = prognoseDTO.erIArbeid?.arbeidFOM,
        antarReturAnnenArbeidsgiver = prognoseDTO.erIArbeid?.annetArbeidPaSikt ?: false,
        tilbakemeldingReturArbeid = prognoseDTO.erIArbeid?.vurderingsdato,
        utenArbeidsgiverAntarTilbakeIArbeid = prognoseDTO.erIkkeIArbeid?.arbeidsforPaSikt ?: false,
        utenArbeidsgiverAntarTilbakeIArbeidDato = prognoseDTO.erIkkeIArbeid?.arbeidsforFOM,
        utenArbeidsgiverTilbakemelding = prognoseDTO.erIkkeIArbeid?.vurderingsdato
    )
}

fun tilUtdypendeOpplysninger(utdypendeOpplysningerMap: Map<String, Map<String, SporsmalSvarDTO>>): UtdypendeOpplysninger {
    val filtrerteUtdypendeOpplysninger: Map<String, Map<String, SporsmalSvarDTO>> = utdypendeOpplysningerMap.mapValues {
        it.value.filterValues { sporsmalSvar -> !sporsmalSvar.restriksjoner.contains(SvarRestriksjonDTO.SKJERMET_FOR_PASIENT) }
    }
    return UtdypendeOpplysninger(
        sykehistorie = filtrerteUtdypendeOpplysninger["6.2"]?.get("6.2.1")?.svar,
        paavirkningArbeidsevne = filtrerteUtdypendeOpplysninger["6.2"]?.get("6.2.2")?.svar,
        resultatAvBehandling = filtrerteUtdypendeOpplysninger["6.2"]?.get("6.2.3")?.svar,
        henvisningUtredningBehandling = filtrerteUtdypendeOpplysninger["6.2"]?.get("6.2.4")?.svar,
        grupper = filtrerteUtdypendeOpplysninger.map { tilSporsmalsGruppe(it) }
    )
}

fun tilSporsmalsGruppe(mapEntry: Map.Entry<String, Map<String, SporsmalSvarDTO>>): Sporsmalsgruppe {
    return Sporsmalsgruppe(
        id = mapEntry.key,
        sporsmal = mapEntry.value.entries.map { tilSporsmal(it) }
    )
}

fun tilSporsmal(sporsmalSvarDTOMapEntry: Map.Entry<String, SporsmalSvarDTO>): Sporsmal {
    return Sporsmal(
        id = sporsmalSvarDTOMapEntry.key,
        svar = sporsmalSvarDTOMapEntry.value.svar
    )
}

fun tilMeldingTilNav(meldingTilNavDTO: MeldingTilNavDTO): MeldingTilNav {
    return MeldingTilNav(
        navBoerTaTakISaken = meldingTilNavDTO.bistandUmiddelbart,
        navBoerTaTakISakenBegrunnelse = meldingTilNavDTO.beskrivBistand
    )
}

fun tilTilbakedatering(kontaktMedPasientDTO: KontaktMedPasientDTO): Tilbakedatering {
    return Tilbakedatering(
        dokumenterbarPasientkontakt = kontaktMedPasientDTO.kontaktDato,
        tilbakedatertBegrunnelse = kontaktMedPasientDTO.begrunnelseIkkeKontakt
    )
}

fun tilBekreftelse(behandlerDTO: BehandlerDTO, behandletTidspunkt: OffsetDateTime): Bekreftelse {
    return Bekreftelse(
        utstedelsesdato = behandletTidspunkt.toLocalDate(),
        sykmelder = if (behandlerDTO.mellomnavn.isNullOrEmpty()) { "${behandlerDTO.fornavn} ${behandlerDTO.etternavn}" } else { "${behandlerDTO.fornavn} ${behandlerDTO.mellomnavn} ${behandlerDTO.etternavn}" },
        sykmelderTlf = if (behandlerDTO.tlf != null) { behandlerDTO.tlf.removePrefix("tel:") } else { behandlerDTO.tlf }
    )
}
