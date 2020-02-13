package no.nav.syfo.sykmeldingstatus.api

import java.time.ZonedDateTime

data class SykmeldingStatusEventDTO(
    val statusEvent: StatusEventDTO,
    val timestamp: ZonedDateTime
)

enum class StatusEventDTO {
    APEN, AVBRUTT, UTGATT, SENDT, BEKREFTET
}

data class SykmeldingStatusDTO(
    val timestamp: ZonedDateTime,
    val statusEvent: StatusEventDTO,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmalOgSvarListe: List<SporsmalOgSvarDTO>?
)

data class SykmeldingSendEventDTO(
    val timestamp: ZonedDateTime,
    val arbeidsgiver: ArbeidsgiverStatusDTO,
    val sporsmalOgSvarListe: List<SporsmalOgSvarDTO>
)

data class ArbeidsgiverStatusDTO(
    val orgnummer: String,
    val juridiskOrgnummer: String?,
    val orgNavn: String
)

data class SykmeldingBekreftEventDTO(
    val timestamp: ZonedDateTime,
    val sporsmalOgSvarListe: List<SporsmalOgSvarDTO>?
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
