package no.nav.syfo.sykmeldingstatus.api.v2

import java.time.LocalDate

data class EndreEgenmeldingsdagerEvent(
    val dager: List<LocalDate>,
    val tekst: String,
)
