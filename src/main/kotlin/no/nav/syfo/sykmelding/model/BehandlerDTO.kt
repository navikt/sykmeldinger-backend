package no.nav.syfo.sykmelding.model

data class BehandlerDTO(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val adresse: AdresseDTO,
    val tlf: String?
)
