package no.nav.syfo.sykmelding.api

import java.time.LocalDate

data class ApiFilter(
    val fom: LocalDate?,
    val tom: LocalDate?,
    val exclude: List<String>?,
    val include: List<String>?,
)
