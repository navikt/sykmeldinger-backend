package no.nav.syfo.sykmeldingstatus

import no.nav.syfo.sykmeldingstatus.api.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.tilSykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.redis.SykmeldingStatusRedisService
import no.nav.syfo.sykmeldingstatus.redis.toSykmeldingRedisModel
import no.nav.syfo.sykmeldingstatus.redis.toSykmeldingStatusRedisModel

class SykmeldingStatusService(private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer, private val sykmeldingStatusJedisService: SykmeldingStatusRedisService) {

    fun registrerStatus(sykmeldingStatusEventDTO: SykmeldingStatusEventDTO, sykmeldingId: String, source: String) {
        sykmeldingStatusKafkaProducer.send(sykmeldingStatusEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId), source)
        sykmeldingStatusJedisService.updateStatus(sykmeldingStatusEventDTO.toSykmeldingRedisModel(), sykmeldingId)
    }

    fun registrerSendt(sykmeldingSendEventDTO: SykmeldingSendEventDTO, sykmeldingId: String, source: String) {
        sykmeldingStatusKafkaProducer.send(sykmeldingSendEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId), source)
        sykmeldingStatusJedisService.updateStatus(sykmeldingSendEventDTO.toSykmeldingStatusRedisModel(), sykmeldingId)
    }

    fun registrerBekreftet(sykmeldingBekreftEventDTO: SykmeldingBekreftEventDTO, sykmeldingId: String, source: String) {
        sykmeldingStatusKafkaProducer.send(sykmeldingBekreftEventDTO.tilSykmeldingStatusKafkaEventDTO(sykmeldingId), source)
        sykmeldingStatusJedisService.updateStatus(sykmeldingBekreftEventDTO.toSykmeldingStatusRedisModel(), sykmeldingId)
    }
}
