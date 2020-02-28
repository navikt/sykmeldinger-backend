package no.nav.syfo.sykmeldingstatus

import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.sykmeldingstatus.api.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisModel

fun getSykmeldingStatus(statusEventDTO: StatusEventDTO, dateTime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): SykmeldingStatusEventDTO {
    return SykmeldingStatusEventDTO(statusEventDTO, dateTime)
}

fun getSykmeldingStatusRedisModel(statusEventDTO: StatusEventDTO, dateTime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): SykmeldingStatusRedisModel {
    return SykmeldingStatusRedisModel(dateTime, statusEventDTO, null, null)
}
