package no.nav.syfo.sykmeldingstatus

import no.nav.syfo.client.SyfosmregisterStatusClient
import no.nav.syfo.log
import no.nav.syfo.sykmeldingstatus.api.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.exception.InvalidSykmeldingStatusException
import no.nav.syfo.sykmeldingstatus.exception.SykmeldingStatusNotFoundException
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.tilSykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisModel
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import no.nav.syfo.sykmeldingstatus.redis.toSykmeldingRedisModel
import no.nav.syfo.sykmeldingstatus.redis.toSykmeldingStatusRedisModel

class SykmeldingStatusService(
    private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer,
    private val sykmeldingStatusJedisService: SykmeldingStatusRedisService,
    private val syfosmregisterStatusClient: SyfosmregisterStatusClient
) {
    companion object {
        private val statusStates: Map<StatusEventDTO, List<StatusEventDTO>> = mapOf(
                Pair(StatusEventDTO.APEN, listOf(StatusEventDTO.BEKREFTET, StatusEventDTO.AVBRUTT, StatusEventDTO.SENDT, StatusEventDTO.APEN, StatusEventDTO.UTGATT)),
                Pair(StatusEventDTO.BEKREFTET, listOf(StatusEventDTO.APEN, StatusEventDTO.AVBRUTT)),
                Pair(StatusEventDTO.SENDT, emptyList()),
                Pair(StatusEventDTO.AVBRUTT, listOf(StatusEventDTO.APEN)),
                Pair(StatusEventDTO.UTGATT, emptyList())
        )
    }

    suspend fun registrerStatus(sykmeldingStatusEventDTO: SykmeldingStatusEventDTO, sykmeldingId: String, source: String, fnr: String, token: String) {
        if (source == "syfoservice") {
            sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO = sykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId), source = source, fnr = fnr)
        } else {
            val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, token)
            if (canChangeStatus(sykmeldingStatusEventDTO.statusEvent, sisteStatus.statusEvent, sykmeldingId)) {
                sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO = sykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId), source = source, fnr = fnr)
                sykmeldingStatusJedisService.updateStatus(sykmeldingStatusEventDTO.toSykmeldingRedisModel(), sykmeldingId)
            }
        }
    }

    suspend fun registrerSendt(
        sykmeldingSendEventDTO: SykmeldingSendEventDTO,
        sykmeldingId: String,
        source: String,
        fnr: String,
        token: String,
        fromSyfoservice: Boolean
    ) {
        if (fromSyfoservice) {
            sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO = sykmeldingSendEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId), source = source, fnr = fnr)
        } else {
            val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, token)
            if (canChangeStatus(StatusEventDTO.SENDT, sisteStatus.statusEvent, sykmeldingId)) {
                sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO = sykmeldingSendEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId), source = source, fnr = fnr)
                sykmeldingStatusJedisService.updateStatus(sykmeldingSendEventDTO.toSykmeldingStatusRedisModel(), sykmeldingId)
            }
        }
    }

    suspend fun registrerBekreftet(sykmeldingBekreftEventDTO: SykmeldingBekreftEventDTO, sykmeldingId: String, source: String, fnr: String, token: String) {
        if (source == "syfoservice") {
            sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO = sykmeldingBekreftEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId), source = source, fnr = fnr)
        } else {
            val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, token)
            if (canChangeStatus(StatusEventDTO.BEKREFTET, sisteStatus.statusEvent, sykmeldingId)) {
                sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO = sykmeldingBekreftEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId), source = source, fnr = fnr)
                sykmeldingStatusJedisService.updateStatus(sykmeldingBekreftEventDTO.toSykmeldingStatusRedisModel(), sykmeldingId)
            }
        }
    }

    private fun getLatestStatus(sykmeldingId: String): SykmeldingStatusEventDTO? {
        return sykmeldingStatusJedisService.getStatus(sykmeldingId)?.toSykmeldingStatusDTO()
    }

    private fun canChangeStatus(nyStatusEvent: StatusEventDTO, sisteStatus: StatusEventDTO, sykmeldingId: String): Boolean {
        val allowedStatuses = statusStates[sisteStatus]
        if (allowedStatuses != null && allowedStatuses.contains(nyStatusEvent)) {
            return true
        }
        throw InvalidSykmeldingStatusException("Kan ikke endre status fra $sisteStatus til $nyStatusEvent for sykmeldingID $sykmeldingId")
    }

    suspend fun hentSisteStatusOgSjekkTilgang(sykmeldingId: String, token: String): SykmeldingStatusEventDTO {
        return try {
            val statusFromRegister = syfosmregisterStatusClient.hentSykmeldingstatus(sykmeldingId = sykmeldingId, token = token)
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
    return SykmeldingStatusEventDTO(timestamp = timestamp,
            statusEvent = statusEvent)
}
