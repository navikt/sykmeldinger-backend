package no.nav.syfo.sykmelding.syforestmodel

import com.fasterxml.jackson.module.kotlin.readValue
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
import no.nav.syfo.sykmelding.model.Sykmelding
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

fun pdlPersonTilPasient(fnr: String, pdlPerson: PdlPerson): Pasient {
    return Pasient(
        fnr = fnr,
        fornavn = pdlPerson.navn.fornavn,
        mellomnavn = pdlPerson.navn.mellomnavn,
        etternavn = pdlPerson.navn.etternavn
    )
}

fun tilSyforestSykmelding(sykmelding: Sykmelding, pasient: Pasient, arbeidsgivervisning: Boolean): SyforestSykmelding {
    val arbeidsgiverNavnHvisSendt = sykmelding.sykmeldingStatus.arbeidsgiver?.orgNavn

    return SyforestSykmelding(
        id = sykmelding.id,
        startLegemeldtFravaer = sykmelding.syketilfelleStartDato,
        skalViseSkravertFelt = !sykmelding.skjermesForPasient,
        identdato = sykmelding.syketilfelleStartDato,
        status = tilStatus(sykmelding.sykmeldingStatus, sykmelding.mottattTidspunkt.toLocalDate()),
        naermesteLederStatus = null, // brukes ikke av frontend
        erEgenmeldt = sykmelding.egenmeldt ?: false,
        erPapirsykmelding = sykmelding.papirsykmelding ?: false,
        innsendtArbeidsgivernavn = arbeidsgiverNavnHvisSendt,
        valgtArbeidssituasjon = finnArbeidssituasjon(sykmelding.sykmeldingStatus, arbeidsgivervisning),
        mottakendeArbeidsgiver = tilMottakendeArbeidsgiver(sykmelding.sykmeldingStatus.arbeidsgiver),
        orgnummer = sykmelding.sykmeldingStatus.arbeidsgiver?.orgnummer,
        sendtdato = finnSendtDato(sykmelding.sykmeldingStatus),
        sporsmal = tilSporsmal(sykmelding.sykmeldingStatus, arbeidsgivervisning),
        pasient = pasient,
        arbeidsgiver = sykmelding.arbeidsgiver?.navn,
        stillingsprosent = sykmelding.arbeidsgiver?.stillingsprosent,
        diagnose = tilDiagnoseinfo(sykmelding.skjermesForPasient, sykmelding.medisinskVurdering, arbeidsgivervisning),
        mulighetForArbeid = tilMulighetForArbeid(sykmelding.sykmeldingsperioder, sykmelding.harRedusertArbeidsgiverperiode, arbeidsgivervisning),
        friskmelding = if (sykmelding.prognose != null) { tilFriskmelding(sykmelding.prognose, arbeidsgivervisning) } else { Friskmelding() },
        utdypendeOpplysninger = tilUtdypendeOpplysninger(sykmelding.utdypendeOpplysninger, arbeidsgivervisning),
        arbeidsevne = tilArbeidsevne(sykmelding, arbeidsgivervisning),
        meldingTilNav = if (sykmelding.meldingTilNAV != null && !arbeidsgivervisning) { tilMeldingTilNav((sykmelding.meldingTilNAV)) } else { MeldingTilNav() },
        innspillTilArbeidsgiver = sykmelding.meldingTilArbeidsgiver,
        tilbakedatering = tilTilbakedatering(sykmelding.kontaktMedPasient, arbeidsgivervisning),
        bekreftelse = tilBekreftelse(sykmelding.behandler, sykmelding.behandletTidspunkt),
        merknader = sykmelding.merknader?.map { Merknad(type = it.type, beskrivelse = it.beskrivelse) }
    )
}

fun tilStatus(sykmeldingStatusDTO: SykmeldingStatusDTO, mottattDato: LocalDate): String =
    if (sykmeldingStatusDTO.statusEvent == "APEN" && mottattDato.isBefore(LocalDate.of(2020, 1, 1))) {
        "UTGAATT"
    } else if (sykmeldingStatusDTO.statusEvent == "APEN") {
        "NY"
    } else {
        sykmeldingStatusDTO.statusEvent
    }

fun finnArbeidssituasjon(sykmeldingStatusDTO: SykmeldingStatusDTO, arbeidsgivervisning: Boolean): String? {
    // for sendte sykmeldinger skal denne være null
    if (!arbeidsgivervisning && sykmeldingStatusDTO.statusEvent == "BEKREFTET") {
        return sykmeldingStatusDTO.sporsmalOgSvarListe.find { it.shortName == ShortNameDTO.ARBEIDSSITUASJON }?.svar?.svar
    }
    return null
}

fun finnSendtDato(sykmeldingStatusDTO: SykmeldingStatusDTO): LocalDateTime? {
    if (sykmeldingStatusDTO.statusEvent == "SENDT" || sykmeldingStatusDTO.statusEvent == "BEKREFTET" || sykmeldingStatusDTO.statusEvent == "AVBRUTT") {
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

fun tilSporsmal(sykmeldingStatusDTO: SykmeldingStatusDTO, arbeidsgivervisning: Boolean): Skjemasporsmal? {
    if (arbeidsgivervisning) {
        return null
    }
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

fun tilDiagnoseinfo(skalSkjermesForPasient: Boolean, medisinskVurdering: MedisinskVurderingDTO?, arbeidsgivervisning: Boolean): Diagnoseinfo {
    if (skalSkjermesForPasient || arbeidsgivervisning) {
        return Diagnoseinfo(bidiagnoser = emptyList())
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

fun tilMulighetForArbeid(periodeliste: List<SykmeldingsperiodeDTO>, harRedusertArbeidsgiverperiode: Boolean?, arbeidsgivervisning: Boolean): MulighetForArbeid {
    return MulighetForArbeid(
        perioder = periodeliste.map { tilPeriode(it, harRedusertArbeidsgiverperiode) },
        aktivitetIkkeMulig433 = tilAktivitetIkkeMulig433(periodeliste, arbeidsgivervisning),
        aktivitetIkkeMulig434 = tilAktivitetIkkeMulig434(periodeliste),
        aarsakAktivitetIkkeMulig433 = tilAarsakAktivitetIkkeMulig433(periodeliste, arbeidsgivervisning),
        aarsakAktivitetIkkeMulig434 = tilAarsakAktivitetIkkeMulig434(periodeliste)
    )
}

fun tilPeriode(sykmeldingsperiodeDTO: SykmeldingsperiodeDTO, harRedusertArbeidsgiverperiode: Boolean?): Periode {
    return Periode(
        fom = sykmeldingsperiodeDTO.fom,
        tom = sykmeldingsperiodeDTO.tom,
        grad = sykmeldingsperiodeDTO.gradert?.grad ?: if (sykmeldingsperiodeDTO.type == PeriodetypeDTO.AKTIVITET_IKKE_MULIG) { 100 } else { null },
        behandlingsdager = sykmeldingsperiodeDTO.behandlingsdager,
        reisetilskudd = if (sykmeldingsperiodeDTO.reisetilskudd || sykmeldingsperiodeDTO.gradert?.reisetilskudd == true) { true } else { null },
        avventende = if (sykmeldingsperiodeDTO.type == PeriodetypeDTO.AVVENTENDE) { sykmeldingsperiodeDTO.innspillTilArbeidsgiver } else { null },
        redusertVenteperiode = if (harRedusertArbeidsgiverperiode == true) { true } else { null }
    )
}

fun tilAktivitetIkkeMulig433(periodeliste: List<SykmeldingsperiodeDTO>, arbeidsgivervisning: Boolean): List<String> {
    if (arbeidsgivervisning) {
        return emptyList()
    }
    return periodeliste.flatMap { sykmeldingsperiodeDTO -> sykmeldingsperiodeDTO.aktivitetIkkeMulig?.medisinskArsak?.arsak?.map { it.text }.orEmpty() }
}

fun tilAktivitetIkkeMulig434(periodeliste: List<SykmeldingsperiodeDTO>): List<String> {
    return periodeliste.flatMap { sykmeldingsperiodeDTO -> sykmeldingsperiodeDTO.aktivitetIkkeMulig?.arbeidsrelatertArsak?.arsak?.map { it.text }.orEmpty() }
}

fun tilAarsakAktivitetIkkeMulig433(periodeliste: List<SykmeldingsperiodeDTO>, arbeidsgivervisning: Boolean): String? {
    if (arbeidsgivervisning) {
        return null
    }
    return periodeliste.mapNotNull { sykmeldingsperiodeDTO -> sykmeldingsperiodeDTO.aktivitetIkkeMulig?.medisinskArsak?.beskrivelse }.firstOrNull()
}

fun tilAarsakAktivitetIkkeMulig434(periodeliste: List<SykmeldingsperiodeDTO>): String? {
    return periodeliste.mapNotNull { sykmeldingsperiodeDTO -> sykmeldingsperiodeDTO.aktivitetIkkeMulig?.arbeidsrelatertArsak?.beskrivelse }.firstOrNull()
}

fun tilFriskmelding(prognoseDTO: PrognoseDTO, arbeidsgivervisning: Boolean): Friskmelding {
    return Friskmelding(
        arbeidsfoerEtterPerioden = prognoseDTO.arbeidsforEtterPeriode,
        hensynPaaArbeidsplassen = prognoseDTO.hensynArbeidsplassen,
        antarReturSammeArbeidsgiver = prognoseDTO.erIArbeid?.egetArbeidPaSikt ?: false,
        antattDatoReturSammeArbeidsgiver = prognoseDTO.erIArbeid?.arbeidFOM,
        antarReturAnnenArbeidsgiver = prognoseDTO.erIArbeid?.annetArbeidPaSikt ?: false,
        tilbakemeldingReturArbeid = if (arbeidsgivervisning) { null } else { prognoseDTO.erIArbeid?.vurderingsdato },
        utenArbeidsgiverAntarTilbakeIArbeid = prognoseDTO.erIkkeIArbeid?.arbeidsforPaSikt ?: false,
        utenArbeidsgiverAntarTilbakeIArbeidDato = prognoseDTO.erIkkeIArbeid?.arbeidsforFOM,
        utenArbeidsgiverTilbakemelding = prognoseDTO.erIkkeIArbeid?.vurderingsdato
    )
}

fun tilUtdypendeOpplysninger(utdypendeOpplysningerMap: Map<String, Map<String, SporsmalSvarDTO>>, arbeidsgivervisning: Boolean): UtdypendeOpplysninger {
    if (arbeidsgivervisning) {
        return UtdypendeOpplysninger()
    }
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

private fun tilArbeidsevne(sykmelding: Sykmelding, arbeidsgivervisning: Boolean): Arbeidsevne {
    if (arbeidsgivervisning) {
        return Arbeidsevne(
            tilretteleggingArbeidsplass = sykmelding.tiltakArbeidsplassen,
            tiltakNAV = null,
            tiltakAndre = null
        )
    }
    return Arbeidsevne(
        tilretteleggingArbeidsplass = sykmelding.tiltakArbeidsplassen,
        tiltakNAV = sykmelding.tiltakNAV,
        tiltakAndre = sykmelding.andreTiltak
    )
}

fun tilMeldingTilNav(meldingTilNavDTO: MeldingTilNavDTO): MeldingTilNav {
    return MeldingTilNav(
        navBoerTaTakISaken = meldingTilNavDTO.bistandUmiddelbart,
        navBoerTaTakISakenBegrunnelse = meldingTilNavDTO.beskrivBistand
    )
}

fun tilTilbakedatering(kontaktMedPasientDTO: KontaktMedPasientDTO, arbeidsgivervisning: Boolean): Tilbakedatering {
    return if (arbeidsgivervisning) {
        Tilbakedatering()
    } else {
        Tilbakedatering(
            dokumenterbarPasientkontakt = kontaktMedPasientDTO.kontaktDato,
            tilbakedatertBegrunnelse = kontaktMedPasientDTO.begrunnelseIkkeKontakt
        )
    }
}

fun tilBekreftelse(behandlerDTO: BehandlerDTO, behandletTidspunkt: OffsetDateTime): Bekreftelse {
    return Bekreftelse(
        utstedelsesdato = behandletTidspunkt.toLocalDate(),
        sykmelder = if (behandlerDTO.mellomnavn.isNullOrEmpty()) { "${behandlerDTO.fornavn} ${behandlerDTO.etternavn}" } else { "${behandlerDTO.fornavn} ${behandlerDTO.mellomnavn} ${behandlerDTO.etternavn}" },
        sykmelderTlf = if (behandlerDTO.tlf != null) { behandlerDTO.tlf.removePrefix("tel:") } else { behandlerDTO.tlf }
    )
}
