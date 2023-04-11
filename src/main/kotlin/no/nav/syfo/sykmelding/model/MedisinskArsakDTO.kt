package no.nav.syfo.sykmelding.model

data class MedisinskArsakDTO(
    val beskrivelse: String?,
    val arsak: List<MedisinskArsakTypeDTO>,
)
