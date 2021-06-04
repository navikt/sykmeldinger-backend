package no.nav.syfo.arbeidsgivere.client.arbeidsforhold.model

import java.time.LocalDate

data class Ansettelsesperiode(
    val periode: Periode
)

data class Periode(
    val fom: LocalDate,
    val tom: LocalDate?
)
