package no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model

data class Arbeidsforhold(
    val arbeidsgiver: Arbeidsgiver,
    val opplysningspliktig: Opplysningspliktig,
    val arbeidsavtaler: List<Arbeidsavtale>
)
