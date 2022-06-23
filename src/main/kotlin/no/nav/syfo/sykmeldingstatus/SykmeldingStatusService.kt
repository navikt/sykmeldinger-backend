package no.nav.syfo.sykmeldingstatus

import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.client.SyfosmregisterStatusClient
import no.nav.syfo.log
import no.nav.syfo.metrics.BEKREFTET_AV_BRUKER_COUNTER
import no.nav.syfo.metrics.SENDT_AV_BRUKER_COUNTER
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v2.ArbeidssituasjonDTO
import no.nav.syfo.sykmeldingstatus.api.v2.SykmeldingUserEvent
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.tilSykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisModel
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import no.nav.syfo.sykmeldingstatus.redis.tilSykmeldingStatusRedisModel
import no.nav.syfo.sykmeldingstatus.redis.toSykmeldingRedisModel
import no.nav.syfo.sykmeldingstatus.redis.toSykmeldingStatusRedisModel
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SykmeldingStatusService(
    private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer,
    private val sykmeldingStatusJedisService: SykmeldingStatusRedisService,
    private val syfosmregisterStatusClient: SyfosmregisterStatusClient,
    private val arbeidsgiverService: ArbeidsgiverService,
) {
    companion object {
        private val statusStates: Map<StatusEventDTO, List<StatusEventDTO>> = mapOf(
            Pair(StatusEventDTO.APEN, listOf(StatusEventDTO.BEKREFTET, StatusEventDTO.AVBRUTT, StatusEventDTO.SENDT, StatusEventDTO.APEN, StatusEventDTO.UTGATT)),
            Pair(StatusEventDTO.BEKREFTET, listOf(StatusEventDTO.APEN, StatusEventDTO.AVBRUTT)),
            Pair(StatusEventDTO.SENDT, emptyList()),
            Pair(StatusEventDTO.AVBRUTT, listOf(StatusEventDTO.APEN)),
            Pair(StatusEventDTO.UTGATT, listOf(StatusEventDTO.AVBRUTT))
        )
        private val statusStatesAvvistSykmelding: Map<StatusEventDTO, List<StatusEventDTO>> = mapOf(
            Pair(StatusEventDTO.APEN, listOf(StatusEventDTO.BEKREFTET)),
            Pair(StatusEventDTO.BEKREFTET, emptyList())
        )
        private val statusStatesEgenmelding: Map<StatusEventDTO, List<StatusEventDTO>> = mapOf(
            Pair(StatusEventDTO.APEN, listOf(StatusEventDTO.BEKREFTET, StatusEventDTO.AVBRUTT)),
            Pair(StatusEventDTO.BEKREFTET, emptyList()),
            Pair(StatusEventDTO.AVBRUTT, emptyList())
        )
    }

    suspend fun registrerStatus(
        sykmeldingStatusEventDTO: SykmeldingStatusEventDTO,
        sykmeldingId: String,
        source: String,
        fnr: String,
        token: String
    ) {
        val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, token)
        if (canChangeStatus(nyStatusEvent = sykmeldingStatusEventDTO.statusEvent, sisteStatus = sisteStatus.statusEvent, erAvvist = sisteStatus.erAvvist, erEgenmeldt = sisteStatus.erEgenmeldt, sykmeldingId = sykmeldingId)) {
            sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO = sykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId), source = source, fnr = fnr)
            sykmeldingStatusJedisService.updateStatus(sykmeldingStatusEventDTO.toSykmeldingRedisModel(), sykmeldingId)
        }
    }

    suspend fun registrerUserEvent(
        sykmeldingUserEvent: SykmeldingUserEvent,
        sykmeldingId: String,
        fnr: String,
        token: String
    ) {
        val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, token)
        val nesteStatus = sykmeldingUserEvent.toStatusEvent()
        if (canChangeStatus(nyStatusEvent = nesteStatus, sisteStatus = sisteStatus.statusEvent, erAvvist = sisteStatus.erAvvist, erEgenmeldt = sisteStatus.erEgenmeldt, sykmeldingId = sykmeldingId)) {
            val arbeidsgiver = when (nesteStatus) {
                StatusEventDTO.SENDT -> getArbeidsgiver(fnr, token, sykmeldingId, sykmeldingUserEvent.arbeidsgiverOrgnummer!!.svar)
                else -> null
            }
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)

            sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO = sykmeldingUserEvent.tilSykmeldingStatusKafkaEventDTO(timestamp, sykmeldingId, arbeidsgiver), source = "user", fnr = fnr)
            sykmeldingStatusJedisService.updateStatus(sykmeldingUserEvent.tilSykmeldingStatusRedisModel(timestamp, arbeidsgiver), sykmeldingId)

            when (nesteStatus) {
                StatusEventDTO.SENDT -> SENDT_AV_BRUKER_COUNTER.inc()
                StatusEventDTO.BEKREFTET -> BEKREFTET_AV_BRUKER_COUNTER.inc()
                else -> Unit
            }
        }
    }

    private suspend fun getArbeidsgiver(fnr: String, token: String, sykmeldingId: String, orgnummer: String): Arbeidsgiverinfo {
        return arbeidsgiverService.getArbeidsgivere(fnr, token, sykmeldingId)
            .find { it.orgnummer == orgnummer }
            ?: throw InvalidSykmeldingStatusException("Kan ikke sende sykmelding $sykmeldingId til orgnummer $orgnummer fordi bruker ikke har arbeidsforhold der")
    }

    suspend fun registrerBekreftetAvvist(sykmeldingId: String, source: String, fnr: String, token: String) {
        val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, token)
        when (sisteStatus.erAvvist) {
            true -> {
                if (canChangeStatus(
                        nyStatusEvent = StatusEventDTO.BEKREFTET,
                        sisteStatus = sisteStatus.statusEvent,
                        erAvvist = true,
                        erEgenmeldt = sisteStatus.erEgenmeldt,
                        sykmeldingId = sykmeldingId
                    )
                ) {
                    val sykmeldingBekreftEventDTO = SykmeldingBekreftEventDTO(
                        timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                        sporsmalOgSvarListe = emptyList(),
                    )
                    sykmeldingStatusKafkaProducer.send(
                        sykmeldingBekreftEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId),
                        source,
                        fnr
                    )
                    sykmeldingStatusJedisService.updateStatus(
                        sykmeldingBekreftEventDTO.toSykmeldingStatusRedisModel(),
                        sykmeldingId
                    )
                } else {
                    log.warn("Kan ikke endre status fra ${sisteStatus.statusEvent} til ${StatusEventDTO.BEKREFTET} for sykmelding med id: $sykmeldingId")
                    throw InvalidSykmeldingStatusException("Kan ikke endre status fra ${sisteStatus.statusEvent} til ${StatusEventDTO.BEKREFTET} for sykmelding med id: $sykmeldingId")
                }
            }
            else -> {
                log.warn("Forsøk på å bekrefte avvist sykmelding som ikke er avvist. SykmeldingId: $sykmeldingId")
                throw InvalidSykmeldingStatusException("Kan ikke bekrefte sykmelding med id: $sykmeldingId fordi den ikke er avvist")
            }
        }
    }

    private fun getLatestStatus(sykmeldingId: String): SykmeldingStatusEventDTO? {
        return sykmeldingStatusJedisService.getStatus(sykmeldingId)?.toSykmeldingStatusDTO()
    }

    private fun canChangeStatus(nyStatusEvent: StatusEventDTO, sisteStatus: StatusEventDTO, erAvvist: Boolean?, erEgenmeldt: Boolean?, sykmeldingId: String): Boolean {
        val allowedStatuses =
            when {
                erAvvist == true -> { statusStatesAvvistSykmelding[sisteStatus] }
                erEgenmeldt == true -> { statusStatesEgenmelding[sisteStatus] }
                else -> { statusStates[sisteStatus] }
            }
        if (allowedStatuses != null && allowedStatuses.contains(nyStatusEvent)) {
            return true
        }
        log.warn("Kan ikke endre status fra $sisteStatus til $nyStatusEvent for sykmeldingID $sykmeldingId")
        throw InvalidSykmeldingStatusException("Kan ikke endre status fra $sisteStatus til $nyStatusEvent for sykmeldingID $sykmeldingId")
    }

    suspend fun hentSisteStatusOgSjekkTilgang(sykmeldingId: String, token: String): SykmeldingStatusEventDTO {
        return try {
            val statusFromRegister = syfosmregisterStatusClient.hentSykmeldingstatusTokenX(sykmeldingId = sykmeldingId, subjectToken = token)
            val statusFromRedis = getLatestStatus(sykmeldingId)
            if (statusFromRedis != null && statusFromRedis.timestamp.isAfter(statusFromRegister.timestamp)) {
                statusFromRedis
            } else {
                statusFromRegister
            }
        } catch (e: Exception) {
            log.error("Could not find sykmeldingstatus for $sykmeldingId", e)
            throw SykmeldingStatusNotFoundException("Fant ikke sykmeldingstatus for sykmelding id $sykmeldingId", e)
        }
    }
}

private fun SykmeldingStatusRedisModel.toSykmeldingStatusDTO(): SykmeldingStatusEventDTO {
    return SykmeldingStatusEventDTO(
        timestamp = timestamp,
        statusEvent = statusEvent,
        erAvvist = erAvvist,
        erEgenmeldt = erEgenmeldt
    )
}

fun SykmeldingUserEvent.toStatusEvent(): StatusEventDTO {
    if (arbeidssituasjon.svar == ArbeidssituasjonDTO.ARBEIDSTAKER) {
        return StatusEventDTO.SENDT
    }
    return StatusEventDTO.BEKREFTET
}
