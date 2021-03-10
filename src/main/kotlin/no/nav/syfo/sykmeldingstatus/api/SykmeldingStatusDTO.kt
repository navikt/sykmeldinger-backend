package no.nav.syfo.sykmeldingstatus.api

import java.time.OffsetDateTime
import java.time.LocalDate

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

// data class SykmeldingBekreftEventUserDTO(
//     val sporsmalOgSvarListe: List<SporsmalOgSvarDTO>?
// )

interface SporsmalSvar<T> {
    val sporsmaltekst: String
    val svartekster: String
    val svar: T
}

data class SykmeldingBekreftEventUserDTO(
    val erOpplysnigeneRiktige: SporsmalSvar<JaEllerNei>,
    val uriktigeOpplysninger: SporsmalSvar<List<UriktigeOpplysningerDTO>>?,
    val arbeidssituasjon: SporsmalSvar<ArbeidssituasjonDTO>,
    val arbeidsgiverOrgnummer: SporsmalSvar<String>?, 
    val nyNarmesteLeder: SporsmalSvar<JaEllerNei>?,
    val harBruktEgenmelding: SporsmalSvar<JaEllerNei>?,
    val egenmeldingsperioder: SporsmalSvar<List<Egenmeldingsperiode>>?,
    val harForsikring: SporsmalSvar<JaEllerNei>?,
) {
    // Validation
    init {
        if (erOpplysnigeneRiktige.svar == JaEllerNei.NEI) {
            requireNotNull(uriktigeOpplysninger)
        }
        if (arbeidssituasjon.svar == ArbeidssituasjonDTO.ARBEIDSGIVER) {
            requireNotNull(arbeidsgiverOrgnummer)
            requireNotNull(nyNarmesteLeder)
        }
        if (harBruktEgenmelding != null) {
            requireNotNull(harForsikring)
            if (harBruktEgenmelding.svar == JaEllerNei.JA) {
                requireNotNull(egenmeldingsperioder)
            }
        }
        if (harForsikring != null) {
            requireNotNull(harBruktEgenmelding)
        }
    }

    fun toSporsmalSvarListe(): List<SporsmalOgSvarDTO> {
        return listOfNotNull(
            arbeidssituasjonSporsmalBuilder(),
            fravarSporsmalBuilder(),
                periodeSporsmalBuilder(),
                nyNarmesteLederSporsmalBuilder(),
                forsikringSporsmalBuilder(),
        )
    }

    private fun arbeidssituasjonSporsmalBuilder(): SporsmalOgSvarDTO {
        return SporsmalOgSvarDTO(
                tekst = arbeidssituasjon.sporsmaltekst,
                shortName = ShortNameDTO.ARBEIDSSITUASJON,
                svartype = SvartypeDTO.ARBEIDSSITUASJON,
                svar = arbeidssituasjon.svar.toString(),
        )
    }

    private fun fravarSporsmalBuilder(): SporsmalOgSvarDTO? {
        if (harBruktEgenmelding != null) {
            return SporsmalOgSvarDTO(
                    tekst = harBruktEgenmelding.sporsmaltekst,
                    shortName = ShortNameDTO.FRAVAER,
                    svartype = SvartypeDTO.JA_NEI,
                    svar = harBruktEgenmelding.svar.toString(),
            )
        }
        return null
    }

    private fun periodeSporsmalBuilder(): SporsmalOgSvarDTO? {
        if (egenmeldingsperioder != null) {
            return SporsmalOgSvarDTO(
                    tekst = egenmeldingsperioder.sporsmaltekst,
                    shortName = ShortNameDTO.PERIODE,
                    svartype = SvartypeDTO.PERIODER,
                    svar = egenmeldingsperioder.svar.toString(),
            )
        }
        return null
    }

    private fun nyNarmesteLederSporsmalBuilder(): SporsmalOgSvarDTO? {
        if (nyNarmesteLeder != null) {
            return SporsmalOgSvarDTO(
                    tekst = nyNarmesteLeder.sporsmaltekst,
                    shortName = ShortNameDTO.NY_NARMESTE_LEDER,
                    svartype = SvartypeDTO.JA_NEI,
                    svar = nyNarmesteLeder.svar.toString(),
            )
        }
        return null
    }

    private fun forsikringSporsmalBuilder(): SporsmalOgSvarDTO? {
        if (harForsikring != null) {
            return SporsmalOgSvarDTO(
                    tekst = harForsikring.sporsmaltekst,
                    shortName = ShortNameDTO.FORSIKRING,
                    svartype = SvartypeDTO.JA_NEI,
                    svar = harForsikring.svar.toString(),
            )
        }
        return null
    }
}

data class Egenmeldingsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
)

enum class JaEllerNei {
    JA,
    NEI,
}

enum class UriktigeOpplysningerDTO{
    PERIODE,
    SYKMELDINGSGRA_FOR_HOY,
    SYKMELDINGSGRA_FOR_LAV,
    ARBEIDSGIVER,
    DIAGNOSE,
    ANDRE_OPPLYSNINGER,
}

enum class ArbeidssituasjonDTO{
    ARBEIDSGIVER,
    FRILANSER,
    SELVSTENDIG_NARINGSDRIVENDE,
    ARBEIDSLEDIG,
    PERMITTERT,
    ANNET,
}

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
