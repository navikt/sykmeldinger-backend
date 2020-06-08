package no.nav.syfo.sykmelding.syforestmodel

data class MulighetForArbeid(
    val perioder: List<Periode>? = null,
    val aktivitetIkkeMulig433: List<String>? = null,
    val aktivitetIkkeMulig434: List<String>? = null,
    val aarsakAktivitetIkkeMulig433: String? = null,
    val aarsakAktivitetIkkeMulig434: String? = null
)
