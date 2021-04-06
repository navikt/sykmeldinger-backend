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
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO

fun SykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId: String): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(sykmeldingId, this.timestamp, this.statusEvent.tilStatusEventDTO(), null, null)
}

fun SykmeldingSendEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId: String): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(sykmeldingId, this.timestamp, STATUS_SENDT, this.arbeidsgiver.tilArbeidsgiverStatusDTO(), tilSporsmalOgSvarDTOListe(this.sporsmalOgSvarListe))
}

fun SykmeldingBekreftEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId: String): SykmeldingStatusKafkaEventDTO {
    return SykmeldingStatusKafkaEventDTO(sykmeldingId, this.timestamp, STATUS_BEKREFTET, null, tilSporsmalOgSvarDTOListe(this.sporsmalOgSvarListe))
}

fun StatusEventDTO.tilStatusEventDTO(): String {
    return when (this) {
        StatusEventDTO.BEKREFTET -> STATUS_BEKREFTET
        StatusEventDTO.APEN -> STATUS_APEN
        StatusEventDTO.SENDT -> STATUS_SENDT
        StatusEventDTO.AVBRUTT -> STATUS_AVBRUTT
        StatusEventDTO.UTGATT -> STATUS_UTGATT
    }
}

fun no.nav.syfo.sykmeldingstatus.api.v1.ArbeidsgiverStatusDTO.tilArbeidsgiverStatusDTO(): ArbeidsgiverStatusDTO {
    return ArbeidsgiverStatusDTO(orgnummer = this.orgnummer, juridiskOrgnummer = this.juridiskOrgnummer, orgNavn = this.orgNavn)
}

fun tilSporsmalOgSvarDTOListe(sporsmalListe: List<no.nav.syfo.sykmeldingstatus.api.v1.SporsmalOgSvarDTO>?): List<SporsmalOgSvarDTO>? {
    return if (sporsmalListe.isNullOrEmpty()) {
        null
    } else {
        sporsmalListe.map { tilSporsmalOgSvarDTO(it) }
    }
}

fun tilSporsmalOgSvarDTO(sporsmalOgSvar: no.nav.syfo.sykmeldingstatus.api.v1.SporsmalOgSvarDTO): SporsmalOgSvarDTO =
    SporsmalOgSvarDTO(tekst = sporsmalOgSvar.tekst, shortName = sporsmalOgSvar.shortName.tilShortNameDTO(), svartype = sporsmalOgSvar.svartype.tilSvartypeDTO(), svar = sporsmalOgSvar.svar)

fun no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.tilShortNameDTO(): ShortNameDTO {
    return when (this) {
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.ARBEIDSSITUASJON -> ShortNameDTO.ARBEIDSSITUASJON
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.PERIODE -> ShortNameDTO.PERIODE
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.FRAVAER -> ShortNameDTO.FRAVAER
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.FORSIKRING -> ShortNameDTO.FORSIKRING
        no.nav.syfo.sykmeldingstatus.api.v1.ShortNameDTO.NY_NARMESTE_LEDER -> ShortNameDTO.NY_NARMESTE_LEDER
    }
}

fun no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO.tilSvartypeDTO(): SvartypeDTO {
    return when (this) {
        no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO.ARBEIDSSITUASJON -> SvartypeDTO.ARBEIDSSITUASJON
        no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO.PERIODER -> SvartypeDTO.PERIODER
        no.nav.syfo.sykmeldingstatus.api.v1.SvartypeDTO.JA_NEI -> SvartypeDTO.JA_NEI
    }
}
