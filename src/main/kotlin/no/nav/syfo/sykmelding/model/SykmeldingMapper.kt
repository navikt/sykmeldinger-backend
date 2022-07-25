package no.nav.syfo.sykmelding.model

import no.nav.syfo.log
import no.nav.syfo.pdl.model.PdlPerson
import java.time.LocalDate

fun Sykmelding.toSykmeldingDTO(fnr: String, pdlPerson: PdlPerson): SykmeldingDTO {
    val pasient = PasientDTO(
        fnr = fnr,
        fornavn = pdlPerson.navn.fornavn,
        mellomnavn = pdlPerson.navn.mellomnavn,
        etternavn = pdlPerson.navn.etternavn
    )

    return SykmeldingDTO(
        id = id,
        mottattTidspunkt = mottattTidspunkt,
        behandlingsutfall = behandlingsutfall,
        legekontorOrgnummer = legekontorOrgnummer,
        arbeidsgiver = arbeidsgiver,
        sykmeldingsperioder = sykmeldingsperioder,
        sykmeldingStatus = sykmeldingStatus,
        medisinskVurdering = medisinskVurdering,
        skjermesForPasient = skjermesForPasient,
        prognose = prognose,
        utdypendeOpplysninger = utdypendeOpplysninger,
        tiltakArbeidsplassen = tiltakArbeidsplassen,
        tiltakNAV = tiltakNAV,
        andreTiltak = andreTiltak,
        meldingTilNAV = meldingTilNAV,
        meldingTilArbeidsgiver = meldingTilArbeidsgiver,
        kontaktMedPasient = kontaktMedPasient,
        behandletTidspunkt = behandletTidspunkt,
        behandler = behandler,
        syketilfelleStartDato = toDate(syketilfelleStartDato),
        navnFastlege = navnFastlege,
        egenmeldt = egenmeldt,
        papirsykmelding = papirsykmelding,
        harRedusertArbeidsgiverperiode = harRedusertArbeidsgiverperiode,
        merknader = merknader,
        pasient = pasient
    )
}

fun toDate(syketilfelleStartDato: LocalDate?): LocalDate? {
    return syketilfelleStartDato?.let {
        when(it.year) {
            in 0..9999 -> it
            else -> {
                log.warn("Ugyldig dato: $it")
                null
            }
        }
    }
}
