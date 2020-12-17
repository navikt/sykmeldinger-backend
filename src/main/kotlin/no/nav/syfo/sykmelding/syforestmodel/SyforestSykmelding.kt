package no.nav.syfo.sykmelding.syforestmodel

import java.time.LocalDate
import java.time.LocalDateTime

data class SyforestSykmelding(
    val id: String? = null,
    val startLegemeldtFravaer: LocalDate? = null,
    val skalViseSkravertFelt: Boolean = false,
    val identdato: LocalDate? = null,
    val status: String? = null,
    val naermesteLederStatus: String? = null,
    val erEgenmeldt: Boolean = false,
    val erPapirsykmelding: Boolean = false,
    val innsendtArbeidsgivernavn: String? = null,
    val valgtArbeidssituasjon: String? = null,
    val mottakendeArbeidsgiver: MottakendeArbeidsgiver? = null,
    val orgnummer: String? = null,
    val sendtdato: LocalDateTime? = null,
    val sporsmal: Skjemasporsmal? = null,
    // 1 PASIENTOPPLYSNINGER
    val pasient: Pasient = Pasient(),
    // 2 ARBEIDSGIVER
    val arbeidsgiver: String? = null,
    val stillingsprosent: Int? = null,
    // 3 DIAGNOSE
    val diagnose: Diagnoseinfo = Diagnoseinfo(),
    // 4 MULIGHET FOR ARBEID
    val mulighetForArbeid: MulighetForArbeid = MulighetForArbeid(),
    // 5 FRISKMELDING
    val friskmelding: Friskmelding = Friskmelding(),
    // 6 UTDYPENDE OPPLYSNINGER
    val utdypendeOpplysninger: UtdypendeOpplysninger = UtdypendeOpplysninger(),
    // 7 HVA SKAL TIL FOR Å BEDRE ARBEIDSEVNEN
    val arbeidsevne: Arbeidsevne = Arbeidsevne(),
    // 8 MELDING TIL NAV
    val meldingTilNav: MeldingTilNav = MeldingTilNav(),
    // 9 MELDING TIL ARBEIDSGIVER
    val innspillTilArbeidsgiver: String? = null,
    // 11 TILBAKEDATERING
    val tilbakedatering: Tilbakedatering = Tilbakedatering(),
    // 12 BEKREFTELSE
    val bekreftelse: Bekreftelse = Bekreftelse(),
    val merknader: List<Merknad>?
)
