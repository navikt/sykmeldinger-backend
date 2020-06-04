package no.nav.syfo.sykmelding.syforestmodel

import java.time.LocalDate

data class Diagnoseinfo(
    val hoveddiagnose: Diagnose? = null,
    val bidiagnoser: List<Diagnose>? = null,
    val fravaersgrunnLovfestet: String? = null,
    val fravaerBeskrivelse: String? = null,
    val svangerskap: Boolean? = null,
    val yrkesskade: Boolean? = null,
    val yrkesskadeDato: LocalDate? = null
)
