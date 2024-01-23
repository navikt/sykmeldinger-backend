package no.nav.syfo.arbeidsgivere.model

data class Arbeidsgiverinfo(
    val orgnummer: String,
    val juridiskOrgnummer: String,
    val navn: String,
    val aktivtArbeidsforhold: Boolean,
    val naermesteLeder: NarmesteLeder?,
)

data class NarmesteLeder(
    val navn: String,
    val orgnummer: String,
    val organisasjonsnavn: String,
)
