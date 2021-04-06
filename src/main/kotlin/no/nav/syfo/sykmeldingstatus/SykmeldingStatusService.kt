package no.nav.syfo.sykmeldingstatus

import no.nav.syfo.arbeidsgivere.model.Arbeidsgiverinfo
import no.nav.syfo.arbeidsgivere.service.ArbeidsgiverService
import no.nav.syfo.client.SyfosmregisterStatusClient
import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.StatusEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.v1.SykmeldingSendEventDTO
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
import no.nav.syfo.sykmeldingstatus.soknadstatus.SoknadstatusService
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SykmeldingStatusService(
    private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer,
    private val sykmeldingStatusJedisService: SykmeldingStatusRedisService,
    private val syfosmregisterStatusClient: SyfosmregisterStatusClient,
    private val soknadstatusService: SoknadstatusService,
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

    suspend fun registrerStatus(sykmeldingStatusEventDTO: SykmeldingStatusEventDTO, sykmeldingId: String, source: String, fnr: String, token: String) {
        if (source == "syfoservice") {
            sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO = sykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId), source = source, fnr = fnr)
            sykmeldingStatusJedisService.updateStatus(sykmeldingStatusEventDTO.toSykmeldingRedisModel(), sykmeldingId)
        } else {
            val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, token)
            if (canChangeStatus(nyStatusEvent = sykmeldingStatusEventDTO.statusEvent, sisteStatus = sisteStatus.statusEvent, erAvvist = sisteStatus.erAvvist, erEgenmeldt = sisteStatus.erEgenmeldt, sykmeldingId = sykmeldingId, token = token)) {
                sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO = sykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId), source = source, fnr = fnr)
                sykmeldingStatusJedisService.updateStatus(sykmeldingStatusEventDTO.toSykmeldingRedisModel(), sykmeldingId)
            }
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
        if (
            canChangeStatus(
                nyStatusEvent = nesteStatus,
                sisteStatus = sisteStatus.statusEvent,
                erAvvist = sisteStatus.erAvvist,
                erEgenmeldt = sisteStatus.erEgenmeldt,
                sykmeldingId = sykmeldingId,
                token = token,
            )
        ) {
            val arbeidsgiver = getArbeidsgivere(fnr, token, sykmeldingId, sykmeldingUserEvent.arbeidsgiverOrgnummer?.svar)
            val timestamp = OffsetDateTime.now(ZoneOffset.UTC)

            sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO = sykmeldingUserEvent.tilSykmeldingStatusKafkaEventDTO(timestamp, sykmeldingId, arbeidsgiver), source = "user", fnr = fnr)
            sykmeldingStatusJedisService.updateStatus(sykmeldingUserEvent.tilSykmeldingStatusRedisModel(timestamp, arbeidsgiver), sykmeldingId)
        }
    }

    private suspend fun getArbeidsgivere(fnr: String, token: String, sykmeldingId: String, orgnummer: String?): Arbeidsgiverinfo? {
        return orgnummer?.let {
            val arbeidsgivere = arbeidsgiverService.getArbeidsgivere(fnr, token, LocalDate.now(), sykmeldingId)
            arbeidsgivere.find { it.orgnummer == orgnummer }
                ?: throw InvalidSykmeldingStatusException("Kan ikke sende sykmelding $sykmeldingId til orgnummer $orgnummer fordi bruker ikke har arbeidsforhold der")
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
            sykmeldingStatusJedisService.updateStatus(sykmeldingSendEventDTO.toSykmeldingStatusRedisModel(), sykmeldingId)
        } else {
            val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, token)
            if (canChangeStatus(nyStatusEvent = StatusEventDTO.SENDT, sisteStatus = sisteStatus.statusEvent, erAvvist = sisteStatus.erAvvist, erEgenmeldt = sisteStatus.erEgenmeldt, sykmeldingId = sykmeldingId, token = token)) {
                sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO = sykmeldingSendEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId), source = source, fnr = fnr)
                sykmeldingStatusJedisService.updateStatus(sykmeldingSendEventDTO.toSykmeldingStatusRedisModel(), sykmeldingId)
            }
        }
    }

    suspend fun registrerBekreftet(sykmeldingBekreftEventDTO: SykmeldingBekreftEventDTO, sykmeldingId: String, source: String, fnr: String, token: String) {
        if (source == "syfoservice") {
            sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO = sykmeldingBekreftEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId), source = source, fnr = fnr)
            sykmeldingStatusJedisService.updateStatus(sykmeldingBekreftEventDTO.toSykmeldingStatusRedisModel(), sykmeldingId)
        } else {
            val sisteStatus = hentSisteStatusOgSjekkTilgang(sykmeldingId, token)
            if (canChangeStatus(nyStatusEvent = StatusEventDTO.BEKREFTET, sisteStatus = sisteStatus.statusEvent, erAvvist = sisteStatus.erAvvist, erEgenmeldt = sisteStatus.erEgenmeldt, sykmeldingId = sykmeldingId, token = token)) {
                sykmeldingStatusKafkaProducer.send(sykmeldingStatusKafkaEventDTO = sykmeldingBekreftEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId), source = source, fnr = fnr)
                sykmeldingStatusJedisService.updateStatus(sykmeldingBekreftEventDTO.toSykmeldingStatusRedisModel(), sykmeldingId)
            }
        }
    }

    private fun getLatestStatus(sykmeldingId: String): SykmeldingStatusEventDTO? {
        return sykmeldingStatusJedisService.getStatus(sykmeldingId)?.toSykmeldingStatusDTO()
    }

    private suspend fun canChangeStatus(nyStatusEvent: StatusEventDTO, sisteStatus: StatusEventDTO, erAvvist: Boolean?, erEgenmeldt: Boolean?, sykmeldingId: String, token: String): Boolean {
        val allowedStatuses =
            when {
                erAvvist == true -> { statusStatesAvvistSykmelding[sisteStatus] }
                erEgenmeldt == true -> { statusStatesEgenmelding[sisteStatus] }
                else -> { statusStates[sisteStatus] }
            }
        if (allowedStatuses != null && allowedStatuses.contains(nyStatusEvent)) {
            if (sisteStatus == StatusEventDTO.BEKREFTET && nyStatusEvent == StatusEventDTO.APEN) {
                val finnesSendtSoknad = soknadstatusService.finnesSendtSoknadForSykmelding(token = token, sykmeldingId = sykmeldingId)
                if (finnesSendtSoknad) {
                    log.warn("Forsøk på å gjenåpne sykmelding som det er sendt søknad for: $sykmeldingId")
                    throw InvalidSykmeldingStatusException("Kan ikke gjenåpne sykmelding med id $sykmeldingId fordi det finnes en sendt søknad for sykmeldingen")
                }
                return true
            }
            return true
        }
        log.warn("Kan ikke endre status fra $sisteStatus til $nyStatusEvent for sykmeldingID $sykmeldingId")
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
