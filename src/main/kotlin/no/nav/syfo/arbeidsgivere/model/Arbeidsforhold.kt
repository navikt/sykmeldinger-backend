package no.nav.syfo.arbeidsgivere.model

import java.time.LocalDate

enum class ArbeidsforholdType {
    FORENKLET_OPPGJOERSORDNING,
    FRILANSER_OPPDRAGSTAKER_HONORAR_PERSONER_MM,
    MARITIMT_ARBEIDSFORHOLD,
    ORDINAERT_ARBEIDSFORHOLD,
    PENSJON_OG_ANDRE_TYPER_YTELSER_UTEN_ANSETTELSESFORHOLD
}

data class Arbeidsforhold(
    val id: Int,
    val fnr: String,
    val orgnummer: String,
    val juridiskOrgnummer: String,
    val orgNavn: String,
    val fom: LocalDate,
    val tom: LocalDate?,
    val type: ArbeidsforholdType? = null,
)
