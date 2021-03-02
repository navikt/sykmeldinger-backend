package no.nav.syfo.pdl.client.model

data class GetPersonResponse(
    val data: ResponseData,
    val errors: List<ResponseError>?
)

data class ResponseData(
    val person: PersonResponse?,
    val identer: IdentResponse?
)

data class PersonResponse(
    val navn: List<Navn>?,
    val adressebeskyttelse: List<Adressebeskyttelse>?
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)

data class Adressebeskyttelse(
    val gradering: Gradering?
)

data class IdentResponse(
    val identer: List<Ident>
)

data class Ident(
    val ident: String,
    val gruppe: String
)

enum class Gradering {
    UGRADERT,
    FORTROLIG,
    STRENGT_FORTROLIG,
    STRENGT_FORTROLIG_UTLAND
}

data class ResponseError(
    val message: String?,
    val locations: List<ErrorLocation>?,
    val path: List<String>?,
    val extensions: ErrorExtension?
)

data class ErrorLocation(
    val line: String?,
    val column: String?
)

data class ErrorExtension(
    val code: String?,
    val details: ErrorDetails?,
    val classification: String?
)

data class ErrorDetails(
    val type: String? = null,
    val cause: String? = null,
    val policy: String? = null
)
