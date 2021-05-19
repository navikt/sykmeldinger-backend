package no.nav.syfo.sykmeldingstatus.api.v2

import java.time.LocalDate

data class SporsmalSvar<T> (
    val sporsmaltekst: String,
    val svartekster: String,
    val svar: T
)

data class SykmeldingUserEvent(
    val erOpplysningeneRiktige: SporsmalSvar<JaEllerNei>,
    val uriktigeOpplysninger: SporsmalSvar<List<UriktigeOpplysningerDTO>>?,
    val arbeidssituasjon: SporsmalSvar<ArbeidssituasjonDTO>,
    val arbeidsgiverOrgnummer: SporsmalSvar<String>?,
    val nyNarmesteLeder: SporsmalSvar<JaEllerNei>?,
    val harBruktEgenmelding: SporsmalSvar<JaEllerNei>?,
    val egenmeldingsperioder: SporsmalSvar<List<Egenmeldingsperiode>>?,
    val harForsikring: SporsmalSvar<JaEllerNei>?,
)

data class Egenmeldingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
)

enum class JaEllerNei {
    JA,
    NEI,
}

enum class UriktigeOpplysningerDTO {
    PERIODE,
    SYKMELDINGSGRA_FOR_HOY,
    SYKMELDINGSGRA_FOR_LAV,
    ARBEIDSGIVER,
    DIAGNOSE,
    ANDRE_OPPLYSNINGER,
}

enum class ArbeidssituasjonDTO {
    ARBEIDSTAKER,
    FRILANSER,
    NAERINGSDRIVENDE,
    ARBEIDSLEDIG,
    ANNET,
}
