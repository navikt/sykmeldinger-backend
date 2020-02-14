package no.nav.syfo.sykmeldingstatus

import no.nav.syfo.sykmeldingstatus.api.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.toSykmeldingStatusKafkaEvent
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import no.nav.syfo.sykmeldingstatus.redis.toSykmeldingRedisModel
import no.nav.syfo.sykmeldingstatus.redis.toSykmeldingStatusRedisModel

class SykmeldingStatusService(private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer, private val sykmeldingStatusJedisService: SykmeldingStatusRedisService) {

    fun registrerStatus(sykmeldingStatusEventDTO: SykmeldingStatusEventDTO, sykmeldingId: String) {
        // tilgangskontroll!
        sykmeldingStatusKafkaProducer.send(sykmeldingStatusEventDTO.toSykmeldingStatusKafkaEvent(sykmeldingId))
        sykmeldingStatusJedisService.updateStatus(sykmeldingStatusEventDTO.toSykmeldingRedisModel(), sykmeldingId)
    }

    fun registrerSendt(sykmeldingSendEventDTO: SykmeldingSendEventDTO, sykmeldingId: String) {
        // tilgangskontroll!
        sykmeldingStatusKafkaProducer.send(sykmeldingSendEventDTO.toSykmeldingStatusKafkaEvent(sykmeldingId))
        sykmeldingStatusJedisService.updateStatus(sykmeldingSendEventDTO.toSykmeldingStatusRedisModel(), sykmeldingId)
    }

    fun registrerBekreftet(sykmeldingBekreftEventDTO: SykmeldingBekreftEventDTO, sykmeldingId: String) {
        // tilgangskontroll!
        sykmeldingStatusKafkaProducer.send(sykmeldingBekreftEventDTO.toSykmeldingStatusKafkaEvent(sykmeldingId))
        sykmeldingStatusJedisService.updateStatus(sykmeldingBekreftEventDTO.toSykmeldingStatusRedisModel(), sykmeldingId)
    }
}
