package no.nav.syfo.sykmelding.syforestmodel

data class MeldingTilNav(
    var navBoerTaTakISaken: Boolean = false,
    var navBoerTaTakISakenBegrunnelse: String? = null
)
