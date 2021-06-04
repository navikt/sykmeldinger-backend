package no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model

data class Arbeidsforhold(
    val arbeidsgiver: Arbeidsgiver,
    val opplysningspliktig: Opplysningspliktig,
    val ansettelsesperiode: Ansettelsesperiode,
    val arbeidsavtaler: List<Arbeidsavtale>
)
