package no.nav.syfo.arbeidsgivere.model

import java.time.LocalDate

data class Arbeidsgiverinfo(
    val orgnummer: String,
    val juridiskOrgnummer: String,
    val navn: String,
    val stillingsprosent: String,
    val stilling: String,
    val aktivtArbeidsforhold: Boolean,
    val naermesteLeder: NarmesteLeder?
)

data class NarmesteLeder(
    val aktoerId: String,
    val navn: String,
    val epost: String?,
    val mobil: String?,
    val orgnummer: String,
    val organisasjonsnavn: String,
    val aktivTom: LocalDate?,
    val arbeidsgiverForskuttererLoenn: Boolean?
)
