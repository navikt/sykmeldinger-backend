package no.nav.syfo.sykmeldingstatus.api.v1

import java.time.LocalDate
import java.time.OffsetDateTime

data class SykmeldingStatusEventDTO(
    val statusEvent: StatusEventDTO,
    val timestamp: OffsetDateTime,
    val erAvvist: Boolean? = null,
    val erEgenmeldt: Boolean? = null
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
    val sporsmalOgSvarListe: List<SporsmalOgSvarDTO>,
    val erAvvist: Boolean? = null,
    val erEgenmeldt: Boolean? = null
)

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

data class SykmeldingBekreftEventUserDTO(
    val sporsmalOgSvarListe: List<SporsmalOgSvarDTO>?
)

data class SporsmalSvar<T> (
    val sporsmaltekst: String,
    val svartekster: String,
    val svar: T
)

data class SykmeldingBekreftEventUserDTOv2(
    val erOpplysnigeneRiktige: SporsmalSvar<JaEllerNei>,
    val uriktigeOpplysninger: SporsmalSvar<List<UriktigeOpplysningerDTO>>?,
    val arbeidssituasjon: SporsmalSvar<ArbeidssituasjonDTO>,
    val arbeidsgiverOrgnummer: SporsmalSvar<String>?,
    val nyNarmesteLeder: SporsmalSvar<JaEllerNei>?,
    val harBruktEgenmelding: SporsmalSvar<JaEllerNei>?,
    val egenmeldingsperioder: SporsmalSvar<List<Egenmeldingsperiode>>?,
    val harForsikring: SporsmalSvar<JaEllerNei>?,
)

data class Egenmeldingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
)

enum class JaEllerNei {
    JA,
    NEI,
}

enum class UriktigeOpplysningerDTO {
    PERIODE,
    SYKMELDINGSGRA_FOR_HOY,
    SYKMELDINGSGRA_FOR_LAV,
    ARBEIDSGIVER,
    DIAGNOSE,
    ANDRE_OPPLYSNINGER,
}

enum class ArbeidssituasjonDTO {
    ARBEIDSTAKER,
    FRILANSER,
    SELVSTENDIG_NARINGSDRIVENDE,
    ARBEIDSLEDIG,
    PERMITTERT,
    ANNET,
}

data class SykmeldingSendEventUserDTO(
    val orgnummer: String,
    val beOmNyNaermesteLeder: Boolean?,
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
