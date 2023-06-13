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
import no.nav.syfo.sykmelding.model.PasientDTO
import no.nav.syfo.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.model.RegelStatusDTO
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO

fun getSykmeldingStatus(
    statusEventDTO: StatusEventDTO,
    dateTime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
    erAvvist: Boolean? = null,
    erEgenmeldt: Boolean? = null,
): SykmeldingStatusEventDTO {
    return SykmeldingStatusEventDTO(statusEventDTO, dateTime, erAvvist, erEgenmeldt)
}

fun getSykmeldingDTO(
    merknader: List<MerknadDTO>? = null,
    timestamps: OffsetDateTime? = null
): SykmeldingDTO {
    return SykmeldingDTO(
        id = "1",
        utdypendeOpplysninger = emptyMap(),
        kontaktMedPasient = KontaktMedPasientDTO(null, null),
        sykmeldingsperioder =
            listOf(
                SykmeldingsperiodeDTO(
                    LocalDate.now(),
                    LocalDate.now(),
                    null,
                    null,
                    null,
                    PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    null,
                    false,
                ),
            ),
        sykmeldingStatus =
            SykmeldingStatusDTO(
                "APEN",
                timestamps ?: OffsetDateTime.now(ZoneOffset.UTC),
                null,
                emptyList(),
            ),
        behandlingsutfall = BehandlingsutfallDTO(RegelStatusDTO.OK, emptyList()),
        medisinskVurdering = getMedisinskVurdering(),
        behandler =
            BehandlerDTO(
                "fornavn",
                null,
                "etternavn",
                AdresseDTO(null, null, null, null, null),
                null,
            ),
        behandletTidspunkt = timestamps ?: OffsetDateTime.now(ZoneOffset.UTC),
        mottattTidspunkt = timestamps ?: OffsetDateTime.now(ZoneOffset.UTC),
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
        merknader = merknader,
        pasient = PasientDTO("12345678901", "fornavn", null, "etternavn"),
        rulesetVersion = null,
        utenlandskSykmelding = null,
    )
}

fun getMedisinskVurdering(): MedisinskVurderingDTO {
    return MedisinskVurderingDTO(
        hovedDiagnose = DiagnoseDTO("1", "system", "hoveddiagnose"),
        biDiagnoser = listOf(DiagnoseDTO("2", "system2", "bidagnose")),
        annenFraversArsak = AnnenFraversArsakDTO("", emptyList()),
        svangerskap = false,
        yrkesskade = false,
        yrkesskadeDato = null,
    )
}
