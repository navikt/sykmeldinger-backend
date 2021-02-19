package no.nav.syfo.sykmeldingstatus

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.sykmelding.model.AdresseDTO
import no.nav.syfo.sykmelding.model.AnnenFraversArsakDTO
import no.nav.syfo.sykmelding.model.BehandlerDTO
import no.nav.syfo.sykmelding.model.BehandlingsutfallDTO
import no.nav.syfo.sykmelding.model.DiagnoseDTO
import no.nav.syfo.sykmelding.model.KontaktMedPasientDTO
import no.nav.syfo.sykmelding.model.MedisinskVurderingDTO
import no.nav.syfo.sykmelding.model.MerknadDTO
import no.nav.syfo.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.model.RegelStatusDTO
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmelding.syforestmodel.Arbeidsevne
import no.nav.syfo.sykmelding.syforestmodel.Bekreftelse
import no.nav.syfo.sykmelding.syforestmodel.Diagnose
import no.nav.syfo.sykmelding.syforestmodel.Diagnoseinfo
import no.nav.syfo.sykmelding.syforestmodel.Friskmelding
import no.nav.syfo.sykmelding.syforestmodel.MeldingTilNav
import no.nav.syfo.sykmelding.syforestmodel.Merknad
import no.nav.syfo.sykmelding.syforestmodel.MulighetForArbeid
import no.nav.syfo.sykmelding.syforestmodel.Pasient
import no.nav.syfo.sykmelding.syforestmodel.Periode
import no.nav.syfo.sykmelding.syforestmodel.SyforestSykmelding
import no.nav.syfo.sykmelding.syforestmodel.Tilbakedatering
import no.nav.syfo.sykmelding.syforestmodel.UtdypendeOpplysninger
import no.nav.syfo.sykmeldingstatus.api.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisModel

fun getSykmeldingStatus(statusEventDTO: StatusEventDTO, dateTime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC), erAvvist: Boolean? = null, erEgenmeldt: Boolean? = null): SykmeldingStatusEventDTO {
    return SykmeldingStatusEventDTO(statusEventDTO, dateTime, erAvvist, erEgenmeldt)
}

fun getSykmeldingStatusRedisModel(statusEventDTO: StatusEventDTO, dateTime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC), erAvvist: Boolean = false, erEgenmeldt: Boolean? = null): SykmeldingStatusRedisModel {
    return SykmeldingStatusRedisModel(dateTime, statusEventDTO, null, null, erAvvist, erEgenmeldt)
}

fun getSykmeldingModel(sykmeldingStatusDTO: SykmeldingStatusDTO = getSykmeldingStatusDto(StatusEventDTO.APEN), merknader: List<MerknadDTO>? = null): SykmeldingDTO {
    return SykmeldingDTO(
        id = "1",
        utdypendeOpplysninger = emptyMap(),
        kontaktMedPasient = KontaktMedPasientDTO(null, null),
        sykmeldingsperioder = listOf(SykmeldingsperiodeDTO(LocalDate.now(), LocalDate.now(), null, null, null, PeriodetypeDTO.AKTIVITET_IKKE_MULIG, null, false)),
        sykmeldingStatus = SykmeldingStatusDTO("APEN", OffsetDateTime.now(ZoneOffset.UTC), null, emptyList()),
        behandlingsutfall = BehandlingsutfallDTO(RegelStatusDTO.OK, emptyList()),
        medisinskVurdering = getMedisinskVurdering(),
        behandler = BehandlerDTO(
            "fornavn", null, "etternavn",
            "123", "01234567891", null, null,
            AdresseDTO(null, null, null, null, null), null),
        behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
        mottattTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
        skjermesForPasient = false,
        meldingTilNAV = null,
        prognose = null,
        arbeidsgiver = null,
        tiltakNAV = null,
        syketilfelleStartDato = null,
        tiltakArbeidsplassen = null,
        navnFastlege = null,
        meldingTilArbeidsgiver = null,
        legekontorOrgnummer = null,
        andreTiltak = null,
        egenmeldt = false,
        harRedusertArbeidsgiverperiode = false,
        papirsykmelding = false,
        merknader = merknader)
}

fun getMedisinskVurdering(): MedisinskVurderingDTO {
    return MedisinskVurderingDTO(
        hovedDiagnose = DiagnoseDTO("1", "system", "hoveddiagnose"),
        biDiagnoser = listOf(DiagnoseDTO("2", "system2", "bidagnose")),
        annenFraversArsak = AnnenFraversArsakDTO("", emptyList()),
        svangerskap = false,
        yrkesskade = false,
        yrkesskadeDato = null
    )
}

fun getSykmeldingStatusDto(statusEventDTO: StatusEventDTO, offsetDateTime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): SykmeldingStatusDTO {
    return SykmeldingStatusDTO(timestamp = offsetDateTime, statusEvent = statusEventDTO.name, arbeidsgiver = null, sporsmalOgSvarListe = emptyList())
}

fun lagSyforestSykmelding(merknader: List<Merknad>? = null): SyforestSykmelding {
    return SyforestSykmelding(
        id = "1",
        startLegemeldtFravaer = null,
        skalViseSkravertFelt = true,
        identdato = null,
        status = "NY",
        naermesteLederStatus = null,
        erEgenmeldt = false,
        erPapirsykmelding = false,
        innsendtArbeidsgivernavn = null,
        valgtArbeidssituasjon = null,
        mottakendeArbeidsgiver = null,
        orgnummer = null,
        sendtdato = null,
        sporsmal = null,
        pasient = Pasient("fnr", "Fornavn", "Mellomnavn", "Etternavn"),
        arbeidsgiver = null,
        stillingsprosent = null,
        diagnose = Diagnoseinfo(
            hoveddiagnose = Diagnose("hoveddiagnose", "1", "system"),
            bidiagnoser = listOf(Diagnose("bidagnose", "2", "system2")),
            fravaersgrunnLovfestet = null,
            fravaerBeskrivelse = "",
            svangerskap = false,
            yrkesskade = false,
            yrkesskadeDato = null
        ),
        mulighetForArbeid = MulighetForArbeid(
            perioder = listOf(Periode(LocalDate.now(), LocalDate.now(), 100, null, null, null)),
            aktivitetIkkeMulig433 = emptyList(),
            aktivitetIkkeMulig434 = emptyList(),
            aarsakAktivitetIkkeMulig433 = null,
            aarsakAktivitetIkkeMulig434 = null
        ),
        friskmelding = Friskmelding(),
        utdypendeOpplysninger = UtdypendeOpplysninger(
            sykehistorie = null,
            paavirkningArbeidsevne = null,
            resultatAvBehandling = null,
            henvisningUtredningBehandling = null,
            grupper = emptyList()
        ),
        arbeidsevne = Arbeidsevne(null, null, null),
        meldingTilNav = MeldingTilNav(),
        innspillTilArbeidsgiver = null,
        tilbakedatering = Tilbakedatering(null, null),
        bekreftelse = Bekreftelse(LocalDate.now(), "fornavn etternavn", null),
        merknader = merknader
    )
}
