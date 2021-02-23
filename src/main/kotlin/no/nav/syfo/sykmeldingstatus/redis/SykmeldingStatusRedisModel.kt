package no.nav.syfo.sykmeldingstatus.redis

import no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.api.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusEventDTO
import java.time.OffsetDateTime

data class SykmeldingStatusRedisModel(
    val timestamp: OffsetDateTime,
    val statusEvent: StatusEventDTO,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmals: List<SporsmalOgSvarDTO>?,
    val erAvvist: Boolean? = null,
    val erEgenmeldt: Boolean? = null
)

fun SykmeldingBekreftEventDTO.toSykmeldingStatusRedisModel(): SykmeldingStatusRedisModel {
    return SykmeldingStatusRedisModel(timestamp, StatusEventDTO.BEKREFTET, null, sporsmalOgSvarListe, erAvvist, erEgenmeldt)
}

fun SykmeldingSendEventDTO.toSykmeldingStatusRedisModel(): SykmeldingStatusRedisModel {
    return SykmeldingStatusRedisModel(timestamp, StatusEventDTO.SENDT, arbeidsgiver, sporsmalOgSvarListe, erAvvist, erEgenmeldt)
}

fun SykmeldingStatusEventDTO.toSykmeldingRedisModel(): SykmeldingStatusRedisModel {
    return SykmeldingStatusRedisModel(timestamp, statusEvent, null, null, erAvvist, erEgenmeldt)
}
