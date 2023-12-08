package no.nav.syfo.sykmelding.db.model

import java.time.LocalDate
import java.time.OffsetDateTime
import no.nav.syfo.model.sykmelding.model.TidligereArbeidsgiverDTO
import no.nav.syfo.sykmelding.model.ArbeidsgiverDTO
import no.nav.syfo.sykmelding.model.BehandlerDTO
import no.nav.syfo.sykmelding.model.KontaktMedPasientDTO
import no.nav.syfo.sykmelding.model.MedisinskVurderingDTO
import no.nav.syfo.sykmelding.model.MeldingTilNavDTO
import no.nav.syfo.sykmelding.model.MerknadDTO
import no.nav.syfo.sykmelding.model.PrognoseDTO
import no.nav.syfo.sykmelding.model.SporsmalSvarDTO
import no.nav.syfo.sykmelding.model.SykmeldingsperiodeDTO
import no.nav.syfo.sykmelding.model.UtenlandskSykmelding

data class SykmeldingDbModel(
    val mottattTidspunkt: OffsetDateTime,
    val legekontorOrgnummer: String?,
    val arbeidsgiver: ArbeidsgiverDTO?,
    val sykmeldingsperioder: List<SykmeldingsperiodeDTO>,
    val medisinskVurdering: MedisinskVurderingDTO?,
    val prognose: PrognoseDTO?,
    val utdypendeOpplysninger: Map<String, Map<String, SporsmalSvarDTO>>,
    val tiltakArbeidsplassen: String?,
    val tiltakNAV: String?,
    val andreTiltak: String?,
    val meldingTilNAV: MeldingTilNavDTO?,
    val meldingTilArbeidsgiver: String?,
    val kontaktMedPasient: KontaktMedPasientDTO,
    val behandletTidspunkt: OffsetDateTime,
    val behandler: BehandlerDTO,
    val syketilfelleStartDato: LocalDate?,
    val navnFastlege: String?,
    val egenmeldt: Boolean?,
    val papirsykmelding: Boolean?,
    val harRedusertArbeidsgiverperiode: Boolean?,
    val merknader: List<MerknadDTO>?,
    val rulesetVersion: String?,
    val utenlandskSykmelding: UtenlandskSykmelding?,
    val tidligereArbeidsgiver: TidligereArbeidsgiverDTO?
)
