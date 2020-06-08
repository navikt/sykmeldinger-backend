package no.nav.syfo.sykmelding.syforestmodel

import java.time.LocalDate

data class Periode(
    val fom: LocalDate? = null,
    val tom: LocalDate? = null,
    val grad: Int? = null,
    val behandlingsdager: Int? = null,
    val reisetilskudd: Boolean? = null,
    val avventende: String? = null,
    val redusertVenteperiode: Boolean? = null
)
