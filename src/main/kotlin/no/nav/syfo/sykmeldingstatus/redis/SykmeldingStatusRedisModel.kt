package no.nav.syfo.sykmeldingstatus.redis

import java.time.OffsetDateTime
import no.nav.syfo.sykmeldingstatus.api.ArbeidsgiverStatusDTO
import no.nav.syfo.sykmeldingstatus.api.SporsmalOgSvarDTO
import no.nav.syfo.sykmeldingstatus.api.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusEventDTO

data class SykmeldingStatusRedisModel(
    val timestamp: OffsetDateTime,
    val statusEvent: StatusEventDTO,
    val arbeidsgiver: ArbeidsgiverStatusDTO?,
    val sporsmals: List<SporsmalOgSvarDTO>?
)

fun SykmeldingBekreftEventDTO.toSykmeldingStatusRedisModel(): SykmeldingStatusRedisModel {
    return SykmeldingStatusRedisModel(timestamp, StatusEventDTO.BEKREFTET, null, sporsmalOgSvarListe)
}

fun SykmeldingSendEventDTO.toSykmeldingStatusRedisModel(): SykmeldingStatusRedisModel {
    return SykmeldingStatusRedisModel(timestamp, StatusEventDTO.SENDT, arbeidsgiver, sporsmalOgSvarListe)
}

fun SykmeldingStatusEventDTO.toSykmeldingRedisModel(): SykmeldingStatusRedisModel {
    return SykmeldingStatusRedisModel(timestamp, statusEvent, null, null)
}
