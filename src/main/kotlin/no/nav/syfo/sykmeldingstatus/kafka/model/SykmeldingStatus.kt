package no.nav.syfo.sykmeldingstatus.kafka.model

const val STATUS_APEN = "APEN"
const val STATUS_AVBRUTT = "AVBRUTT"
const val STATUS_UTGATT = "UTGATT"
const val STATUS_SENDT = "SENDT"
const val STATUS_BEKREFTET = "BEKREFTET"
const val STATUS_SLETTET = "SLETTET"

data class ArbeidsgiverStatusKafkaDTO(
    val orgnummer: String,
    val juridiskOrgnummer: String? = null,
    val orgNavn: String
)

data class SporsmalOgSvarKafkaDTO(
    val tekst: String,
    val shortName: ShortNameKafkaDTO,
    val svartype: SvartypeKafkaDTO,
    val svar: String
)

enum class ShortNameKafkaDTO {
    ARBEIDSSITUASJON,
    NY_NARMESTE_LEDER,
    FRAVAER,
    PERIODE,
    FORSIKRING,
    EGENMELDINGSDAGER
}

enum class SvartypeKafkaDTO {
    ARBEIDSSITUASJON,
    PERIODER,
    JA_NEI,
    DAGER
}

data class TidligereArbeidsgiverKafkaDTO(
    val orgNavn: String,
    val orgnummer: String,
    val sykmeldingsId: String,
)
