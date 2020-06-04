package no.nav.syfo.sykmelding.syforestmodel

data class Sporsmalsgruppe(
    val id: String? = null,
    val sporsmal: List<Sporsmal> = ArrayList()
)
