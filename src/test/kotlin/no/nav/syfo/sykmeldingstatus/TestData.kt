package no.nav.syfo.sykmeldingstatus

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.sykmelding.api.BehandlingsutfallDTO
import no.nav.syfo.sykmelding.api.BehandlingsutfallStatusDTO
import no.nav.syfo.sykmelding.api.PeriodetypeDTO
import no.nav.syfo.sykmelding.api.SykmeldingDTO
import no.nav.syfo.sykmelding.api.SykmeldingsperiodeDTO
import no.nav.syfo.sykmeldingstatus.api.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisModel

fun getSykmeldingStatus(statusEventDTO: StatusEventDTO, dateTime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): SykmeldingStatusEventDTO {
    return SykmeldingStatusEventDTO(statusEventDTO, dateTime)
}

fun getSykmeldingStatusRedisModel(statusEventDTO: StatusEventDTO, dateTime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): SykmeldingStatusRedisModel {
    return SykmeldingStatusRedisModel(dateTime, statusEventDTO, null, null)
}

fun getSykmeldingModel(sykmeldingStatusDTO: SykmeldingStatusDTO = getSykmeldingStatusDto(StatusEventDTO.APEN)): SykmeldingDTO {
    return SykmeldingDTO(
            id = UUID.randomUUID().toString(),
            mottattTidspunkt = LocalDateTime.now().atOffset(ZoneOffset.UTC).toLocalDateTime(),
            arbeidsgiver = null,
            legekontorOrgnummer = "legekontor",
            medisinskVurdering = null,
            behandlingsutfall = BehandlingsutfallDTO(emptyList(), BehandlingsutfallStatusDTO.OK),
            sykmeldingStatus = sykmeldingStatusDTO,
            sykmeldingsperioder = listOf(SykmeldingsperiodeDTO(
                    LocalDate.now(),
                    LocalDate.now(),
                    null,
                    null,
                    null,
                    PeriodetypeDTO.AKTIVITET_IKKE_MULIG
            )),
            legeNavn = "lege",
            bekreftetDato = LocalDateTime.now(),
            egenmeldt = false,
            papirsykmelding = false,
            harRedusertArbeidsgiverperiode = false
            )
}

fun getSykmeldingStatusDto(statusEventDTO: StatusEventDTO, offsetDateTime: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): SykmeldingStatusDTO {
    return SykmeldingStatusDTO(timestamp = offsetDateTime, statusEvent = statusEventDTO.name, arbeidsgiver = null, sporsmalOgSvarListe = emptyList())
}
