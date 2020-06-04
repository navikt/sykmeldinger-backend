package no.nav.syfo.sykmelding.syforestmodel

data class Skjemasporsmal(
    val arbeidssituasjon: String? = null,
    val harForsikring: Boolean? = null,
    val fravaersperioder: List<Datospenn>? = null,
    val harAnnetFravaer: Boolean? = null
)
