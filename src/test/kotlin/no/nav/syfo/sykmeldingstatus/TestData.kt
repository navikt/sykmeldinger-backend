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
import no.nav.syfo.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.model.RegelStatusDTO
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmeldingstatus.api.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisModel

fun getSykmeldingStatus(statusEventDTO: StatusEventDTO, dateTime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): SykmeldingStatusEventDTO {
    return SykmeldingStatusEventDTO(statusEventDTO, dateTime)
}

fun getSykmeldingStatusRedisModel(statusEventDTO: StatusEventDTO, dateTime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): SykmeldingStatusRedisModel {
    return SykmeldingStatusRedisModel(dateTime, statusEventDTO, null, null)
}

fun getSykmeldingModel(sykmeldingStatusDTO: SykmeldingStatusDTO = getSykmeldingStatusDto(StatusEventDTO.APEN)): SykmeldingDTO {
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
        papirsykmelding = false)
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
