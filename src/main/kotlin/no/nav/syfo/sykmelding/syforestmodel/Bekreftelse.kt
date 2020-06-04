package no.nav.syfo.sykmelding.syforestmodel

import java.time.LocalDate

data class Bekreftelse(
    val utstedelsesdato: LocalDate? = null,
    val sykmelder: String? = null,
    val sykmelderTlf: String? = null
)
