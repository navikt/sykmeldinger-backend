package no.nav.syfo.sykmelding.syforestmodel

import java.time.LocalDate

data class Tilbakedatering(
    val dokumenterbarPasientkontakt: LocalDate? = null,
    val tilbakedatertBegrunnelse: String? = null
)
