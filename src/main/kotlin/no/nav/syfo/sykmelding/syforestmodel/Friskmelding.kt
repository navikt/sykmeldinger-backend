package no.nav.syfo.sykmelding.syforestmodel

import java.time.LocalDate

data class Friskmelding(
    val arbeidsfoerEtterPerioden: Boolean? = null,
    val hensynPaaArbeidsplassen: String? = null,
    val antarReturSammeArbeidsgiver: Boolean = false,
    val antattDatoReturSammeArbeidsgiver: LocalDate? = null,
    val antarReturAnnenArbeidsgiver: Boolean = false,
    val tilbakemeldingReturArbeid: LocalDate? = null,
    val utenArbeidsgiverAntarTilbakeIArbeid: Boolean = false,
    val utenArbeidsgiverAntarTilbakeIArbeidDato: LocalDate? = null,
    val utenArbeidsgiverTilbakemelding: LocalDate? = null
)
