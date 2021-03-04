package no.nav.syfo.pdl.model

data class PdlPerson(
    val navn: Navn,
    val aktorId: String,
    val diskresjonskode: Boolean
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)
