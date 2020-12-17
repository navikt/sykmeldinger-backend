package no.nav.syfo.sykmeldingstatus.kafka

import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import no.nav.syfo.model.sykmeldingstatus.STATUS_APEN
import no.nav.syfo.model.sykmeldingstatus.STATUS_AVBRUTT
import no.nav.syfo.model.sykmeldingstatus.STATUS_BEKREFTET
import no.nav.syfo.model.sykmeldingstatus.STATUS_SENDT
import no.nav.syfo.model.sykmeldingstatus.STATUS_UTGATT
import no.nav.syfo.model.sykmeldingstatus.ShortNameDTO
import no.nav.syfo.model.sykmeldingstatus.SporsmalOgSvarDTO
import no.nav.syfo.model.sykmeldingstatus.SvartypeDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusEventDTO

fun SykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId: String): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(sykmeldingId, this.timestamp, this.statusEvent.tilStatusEventDTO(), null, null)
}

fun SykmeldingSendEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId: String): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(sykmeldingId, this.timestamp, STATUS_SENDT, this.arbeidsgiver.tilArbeidsgiverStatusDTO(), tilSporsmalOgSvarDTOListe(this.sporsmalOgSvarListe))
}

fun SykmeldingBekreftEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId: String): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(sykmeldingId, this.timestamp, STATUS_BEKREFTET, null, tilSporsmalOgSvarDTOListe(this.sporsmalOgSvarListe))
}

fun no.nav.syfo.sykmeldingstatus.api.StatusEventDTO.tilStatusEventDTO(): String {
    return when (this) {
        no.nav.syfo.sykmeldingstatus.api.StatusEventDTO.BEKREFTET -> STATUS_BEKREFTET
        no.nav.syfo.sykmeldingstatus.api.StatusEventDTO.APEN -> STATUS_APEN
        no.nav.syfo.sykmeldingstatus.api.StatusEventDTO.SENDT -> STATUS_SENDT
        no.nav.syfo.sykmeldingstatus.api.StatusEventDTO.AVBRUTT -> STATUS_AVBRUTT
        no.nav.syfo.sykmeldingstatus.api.StatusEventDTO.UTGATT -> STATUS_UTGATT
    }
}

fun no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverStatusDTO.tilArbeidsgiverStatusDTO(): ArbeidsgiverStatusDTO {
    return ArbeidsgiverStatusDTO(orgnummer = this.orgnummer, juridiskOrgnummer = this.juridiskOrgnummer, orgNavn = this.orgNavn)
}

fun tilSporsmalOgSvarDTOListe(sporsmalListe: List<no.nav.syfo.sykmeldingstatus.api.SporsmalOgSvarDTO>?): List<SporsmalOgSvarDTO>? {
    return if (sporsmalListe.isNullOrEmpty()) {
        null
    } else {
        sporsmalListe.map { tilSporsmalOgSvarDTO(it) }
    }
}

fun tilSporsmalOgSvarDTO(sporsmalOgSvar: no.nav.syfo.sykmeldingstatus.api.SporsmalOgSvarDTO): SporsmalOgSvarDTO =
    SporsmalOgSvarDTO(tekst = sporsmalOgSvar.tekst, shortName = sporsmalOgSvar.shortName.tilShortNameDTO(), svartype = sporsmalOgSvar.svartype.tilSvartypeDTO(), svar = sporsmalOgSvar.svar)

fun no.nav.syfo.sykmeldingstatus.api.ShortNameDTO.tilShortNameDTO(): ShortNameDTO {
    return when (this) {
        no.nav.syfo.sykmeldingstatus.api.ShortNameDTO.ARBEIDSSITUASJON -> ShortNameDTO.ARBEIDSSITUASJON
        no.nav.syfo.sykmeldingstatus.api.ShortNameDTO.PERIODE -> ShortNameDTO.PERIODE
        no.nav.syfo.sykmeldingstatus.api.ShortNameDTO.FRAVAER -> ShortNameDTO.FRAVAER
        no.nav.syfo.sykmeldingstatus.api.ShortNameDTO.FORSIKRING -> ShortNameDTO.FORSIKRING
        no.nav.syfo.sykmeldingstatus.api.ShortNameDTO.NY_NARMESTE_LEDER -> ShortNameDTO.NY_NARMESTE_LEDER
    }
}

fun no.nav.syfo.sykmeldingstatus.api.SvartypeDTO.tilSvartypeDTO(): SvartypeDTO {
    return when (this) {
        no.nav.syfo.sykmeldingstatus.api.SvartypeDTO.ARBEIDSSITUASJON -> SvartypeDTO.ARBEIDSSITUASJON
        no.nav.syfo.sykmeldingstatus.api.SvartypeDTO.PERIODER -> SvartypeDTO.PERIODER
        no.nav.syfo.sykmeldingstatus.api.SvartypeDTO.JA_NEI -> SvartypeDTO.JA_NEI
    }
}
