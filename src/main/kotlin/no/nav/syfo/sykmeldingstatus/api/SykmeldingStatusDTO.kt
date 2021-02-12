package no.nav.syfo.sykmeldingstatus.api

import java.time.OffsetDateTime

data class SykmeldingStatusEventDTO(
    val statusEvent: StatusEventDTO,
    val timestamp: OffsetDateTime
)

data class SykmeldingStatusDTO(
    val statusEvent: String,
    val timestamp: OffsetDateTime,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmalOgSvarListe: List<SporsmalDTO>
)

enum class StatusEventDTO {
    APEN, AVBRUTT, UTGATT, SENDT, BEKREFTET
}

data class SporsmalDTO(
    val tekst: String,
    val shortName: ShortNameDTO,
    val svar: SvarDTO
)

data class SvarDTO(
    val svarType: SvartypeDTO,
    val svar: String
)

data class SykmeldingSendEventDTO(
    val timestamp: OffsetDateTime,
    val arbeidsgiver: ArbeidsgiverStatusDTO,
    val sporsmalOgSvarListe: List<SporsmalOgSvarDTO>
)

data class ArbeidsgiverStatusDTO(
    val orgnummer: String,
    val juridiskOrgnummer: String?,
    val orgNavn: String
)

data class SykmeldingBekreftEventDTO(
    val timestamp: OffsetDateTime,
    val sporsmalOgSvarListe: List<SporsmalOgSvarDTO>?
)

data class SykmeldingBekreftEventUserDTO(
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
