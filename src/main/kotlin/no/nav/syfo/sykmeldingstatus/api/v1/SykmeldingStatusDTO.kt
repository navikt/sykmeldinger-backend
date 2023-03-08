package no.nav.syfo.sykmeldingstatus.api.v1

import java.time.OffsetDateTime

data class SykmeldingStatusEventDTO(
    val statusEvent: StatusEventDTO,
    val timestamp: OffsetDateTime,
    val erAvvist: Boolean? = null,
    val erEgenmeldt: Boolean? = null
)

enum class StatusEventDTO {
    APEN, AVBRUTT, UTGATT, SENDT, BEKREFTET
}

data class ArbeidsgiverStatusDTO(
    val orgnummer: String,
    val juridiskOrgnummer: String?,
    val orgNavn: String
)

data class SykmeldingBekreftEventDTO(
    val timestamp: OffsetDateTime,
    val sporsmalOgSvarListe: List<SporsmalOgSvarDTO>?,
    val erAvvist: Boolean? = null,
    val erEgenmeldt: Boolean? = null
)

data class SporsmalOgSvarDTO(
    val tekst: String,
    val shortName: ShortNameDTO,
    val svartype: SvartypeDTO,
    val svar: String
)

enum class ShortNameDTO {
    ARBEIDSSITUASJON, NY_NARMESTE_LEDER, FRAVAER, PERIODE, FORSIKRING
}

enum class SvartypeDTO {
    ARBEIDSSITUASJON,
    PERIODER,
    JA_NEI
}
