package no.nav.syfo.sykmeldingstatus

import io.mockk.MockKMatcherScope
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
import no.nav.syfo.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon
import no.nav.syfo.sykmeldingstatus.api.v2.Egenmeldingsperiode
import no.nav.syfo.sykmeldingstatus.api.v2.JaEllerNei
import no.nav.syfo.sykmeldingstatus.api.v2.SporsmalSvar
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingFormResponse
import no.nav.syfo.sykmeldingstatus.kafka.SykmeldingWithArbeidsgiverStatus
import no.nav.syfo.sykmeldingstatus.kafka.model.STATUS_APEN
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaEventDTO

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
    timestamps: OffsetDateTime? = null,
    fom: LocalDate = LocalDate.now(),
    tom: LocalDate = LocalDate.now(),
) =
    SykmeldingDTO(
        id = "1",
        utdypendeOpplysninger = emptyMap(),
        kontaktMedPasient = KontaktMedPasientDTO(null, null),
        sykmeldingsperioder =
            listOf(
                SykmeldingsperiodeDTO(
                    fom,
                    tom,
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
                statusEvent = "APEN",
                timestamp = timestamps ?: OffsetDateTime.now(ZoneOffset.UTC),
                arbeidsgiver = null,
                sporsmalOgSvarListe = emptyList(),
                brukerSvar = null,
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

fun getTidligereArbeidsgiver(): TidligereArbeidsgiverDTO {
    return TidligereArbeidsgiverDTO(
        orgNavn = "Organisasjonen",
        orgnummer = "123123123",
        sykmeldingsId = "1"
    )
}

fun getMedisinskVurdering() =
    MedisinskVurderingDTO(
        hovedDiagnose = DiagnoseDTO("1", "system", "hoveddiagnose"),
        biDiagnoser = listOf(DiagnoseDTO("2", "system2", "bidagnose")),
        annenFraversArsak = AnnenFraversArsakDTO("", emptyList()),
        svangerskap = false,
        yrkesskade = false,
        yrkesskadeDato = null,
    )

internal fun opprettSykmelding(
    fom: LocalDate,
    tom: LocalDate,
    orgnummer: String? = null,
    status: String = STATUS_APEN,
    sykmeldingId: String = "1",
    tidligereArbeidsgiver: TidligereArbeidsgiverDTO? = null,
): SykmeldingWithArbeidsgiverStatus {
    val arbeidsgiver =
        if (orgnummer != null) {
            ArbeidsgiverStatusDTO(orgnummer, "juridiskOrgnummer", "orgNavn")
        } else {
            null
        }
    return SykmeldingWithArbeidsgiverStatus(
        sykmeldingId = sykmeldingId,
        sykmeldingsperioder =
            listOf(
                SykmeldingsperiodeDTO(
                    fom,
                    tom,
                    null,
                    null,
                    null,
                    PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    null,
                    false,
                ),
            ),
        statusEvent = status,
        arbeidsgiver = arbeidsgiver,
        tidligereArbeidsgiver = tidligereArbeidsgiver,
    )
}

internal fun MockKMatcherScope.statusEquals(statusEvent: String) =
    match<SykmeldingStatusKafkaEventDTO> { statusEvent == it.statusEvent }

internal fun MockKMatcherScope.matchStatusWithEmptySporsmals(statusEvent: String) =
    match<SykmeldingStatusKafkaEventDTO> {
        statusEvent == it.statusEvent && it.sporsmals?.isEmpty() ?: true
    }

internal fun opprettSendtSykmeldingUserEvent() =
    SykmeldingFormResponse(
        erOpplysningeneRiktige =
            SporsmalSvar(
                sporsmaltekst = "",
                svar = JaEllerNei.JA,
            ),
        uriktigeOpplysninger = null,
        arbeidssituasjon =
            SporsmalSvar(
                sporsmaltekst = "",
                svar = Arbeidssituasjon.ARBEIDSTAKER,
            ),
        arbeidsgiverOrgnummer =
            SporsmalSvar(
                sporsmaltekst = "",
                svar = "orgnummer",
            ),
        riktigNarmesteLeder =
            SporsmalSvar(
                sporsmaltekst = "",
                svar = JaEllerNei.JA,
            ),
        arbeidsledig = null,
        harBruktEgenmelding = null,
        egenmeldingsperioder = null,
        harForsikring = null,
        harBruktEgenmeldingsdager = null,
        egenmeldingsdager = null,
        fisker = null,
    )

internal fun opprettBekreftetSykmeldingUserEvent(
    arbeidssituasjon: Arbeidssituasjon = Arbeidssituasjon.FRILANSER
) =
    SykmeldingFormResponse(
        erOpplysningeneRiktige =
            SporsmalSvar(
                sporsmaltekst = "",
                svar = JaEllerNei.JA,
            ),
        uriktigeOpplysninger = null,
        arbeidssituasjon =
            SporsmalSvar(
                sporsmaltekst = "",
                svar = arbeidssituasjon,
            ),
        arbeidsgiverOrgnummer = null,
        arbeidsledig = null,
        riktigNarmesteLeder = null,
        harBruktEgenmelding =
            SporsmalSvar(
                sporsmaltekst = "",
                svar = JaEllerNei.JA,
            ),
        egenmeldingsperioder =
            SporsmalSvar(
                sporsmaltekst = "",
                svar =
                    listOf(
                        Egenmeldingsperiode(
                            fom = LocalDate.now().minusWeeks(1),
                            tom = LocalDate.now(),
                        ),
                    ),
            ),
        harForsikring =
            SporsmalSvar(
                sporsmaltekst = "",
                svar = JaEllerNei.JA,
            ),
        harBruktEgenmeldingsdager = null,
        egenmeldingsdager = null,
        fisker = null,
    )
