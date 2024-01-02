package no.nav.syfo.sykmeldingstatus.kafka.model

import java.time.LocalDate
import no.nav.syfo.sykmeldingstatus.api.v2.Arbeidssituasjon
import no.nav.syfo.sykmeldingstatus.api.v2.Blad
import no.nav.syfo.sykmeldingstatus.api.v2.Egenmeldingsperiode
import no.nav.syfo.sykmeldingstatus.api.v2.JaEllerNei
import no.nav.syfo.sykmeldingstatus.api.v2.LottOgHyre
import no.nav.syfo.sykmeldingstatus.api.v2.SporsmalSvar
import no.nav.syfo.sykmeldingstatus.api.v2.UriktigeOpplysningerType

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
    EGENMELDINGSDAGER,
}

enum class SvartypeKafkaDTO {
    ARBEIDSSITUASJON,
    PERIODER,
    JA_NEI,
    DAGER,
}

data class TidligereArbeidsgiverKafkaDTO(
    val orgNavn: String,
    val orgnummer: String,
    val sykmeldingsId: String,
)

data class KomplettInnsendtSkjemaSvar(
    val erOpplysningeneRiktige: SporsmalSvar<JaEllerNei>,
    val uriktigeOpplysninger: SporsmalSvar<List<UriktigeOpplysningerType>>?,
    val arbeidssituasjon: SporsmalSvar<Arbeidssituasjon>,
    val arbeidsgiverOrgnummer: SporsmalSvar<String>?,
    val riktigNarmesteLeder: SporsmalSvar<JaEllerNei>?,
    val harBruktEgenmelding: SporsmalSvar<JaEllerNei>?,
    val egenmeldingsperioder: SporsmalSvar<List<Egenmeldingsperiode>>?,
    val harForsikring: SporsmalSvar<JaEllerNei>?,
    val egenmeldingsdager: SporsmalSvar<List<LocalDate>>?,
    val harBruktEgenmeldingsdager: SporsmalSvar<JaEllerNei>?,
    val fisker: FiskereSvarKafkaDTO?,
)

data class FiskereSvarKafkaDTO(
    val blad: SporsmalSvar<Blad>,
    val lottOgHyre: SporsmalSvar<LottOgHyre>,
)
