package no.nav.syfo.sykmelding.model

data class ArbeidsrelatertArsakDTO(
    val beskrivelse: String?,
    val arsak: List<ArbeidsrelatertArsakTypeDTO>,
)
