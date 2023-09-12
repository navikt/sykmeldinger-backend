package no.nav.syfo.sykmeldingstatus

import io.mockk.MockKMatcherScope
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmelding.model.AdresseDTO
import no.nav.syfo.sykmelding.model.BehandlerDTO
import no.nav.syfo.sykmelding.model.BehandlingsutfallDTO
import no.nav.syfo.sykmelding.model.KontaktMedPasientDTO
import no.nav.syfo.sykmelding.model.PasientDTO
import no.nav.syfo.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.model.RegelStatusDTO
import no.nav.syfo.sykmelding.model.SykmeldingDTO
import no.nav.syfo.sykmelding.model.SykmeldingStatusDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.v2.ArbeidssituasjonDTO
import no.nav.syfo.sykmeldingstatus.api.v2.Egenmeldingsperiode
import no.nav.syfo.sykmeldingstatus.api.v2.JaEllerNei
import no.nav.syfo.sykmeldingstatus.api.v2.SporsmalSvar
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingUserEvent
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal class TestHelper {
    companion object {
        internal fun Int.januar(year: Int) = LocalDate.of(year, 1, this)
        internal fun opprettTidligereSykmelding(fom: LocalDate, tom: LocalDate, orgnummer: String = "orgnummer") = SykmeldingDTO(
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
                "SENDT",
                OffsetDateTime.now(ZoneOffset.UTC),
                ArbeidsgiverStatusDTO(orgnummer, "juridiskOrgnummer", "orgNavn"),
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
            merknader = null,
            pasient = PasientDTO("12345678901", "fornavn", null, "etternavn"),
            rulesetVersion = null,
            utenlandskSykmelding = null,
        )

        internal fun MockKMatcherScope.statusEquals(statusEvent: String) =
            match<SykmeldingStatusKafkaEventDTO> { statusEvent == it.statusEvent }

        internal fun MockKMatcherScope.matchStatusWithEmptySporsmals(statusEvent: String) =
            match<SykmeldingStatusKafkaEventDTO> {
                statusEvent == it.statusEvent && it.sporsmals?.isEmpty() ?: true
            }

        internal fun opprettSendtSykmeldingUserEvent() =
            SykmeldingUserEvent(
                erOpplysningeneRiktige =
                SporsmalSvar(
                    sporsmaltekst = "",
                    svar = JaEllerNei.JA,
                ),
                uriktigeOpplysninger = null,
                arbeidssituasjon =
                SporsmalSvar(
                    sporsmaltekst = "",
                    svar = ArbeidssituasjonDTO.ARBEIDSTAKER,
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
                harBruktEgenmelding = null,
                egenmeldingsperioder = null,
                harForsikring = null,
                harBruktEgenmeldingsdager = null,
                egenmeldingsdager = null,
            )

        internal fun opprettBekreftetSykmeldingUserEvent(arbeidssituasjon: ArbeidssituasjonDTO = ArbeidssituasjonDTO.FRILANSER) =
            SykmeldingUserEvent(
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
            )
    }
}
