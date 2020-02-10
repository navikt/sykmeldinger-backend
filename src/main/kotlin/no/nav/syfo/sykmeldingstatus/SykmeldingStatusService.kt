package no.nav.syfo.sykmeldingstatus

import no.nav.syfo.sykmeldingstatus.api.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.toSykmeldingStatusKafkaEvent
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer

class SykmeldingStatusService(private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer) {

    fun registrerStatus(sykmeldingStatusEventDTO: SykmeldingStatusEventDTO, sykmeldingId: String) {
        // tilgangskontroll!
        sykmeldingStatusKafkaProducer.send(sykmeldingStatusEventDTO.toSykmeldingStatusKafkaEvent(sykmeldingId))
    }

    fun registrerSendt(sykmeldingSendEventDTO: SykmeldingSendEventDTO, sykmeldingId: String) {
        // tilgangskontroll!
        sykmeldingStatusKafkaProducer.send(sykmeldingSendEventDTO.toSykmeldingStatusKafkaEvent(sykmeldingId))
    }

    fun registrerBekreftet(sykmeldingBekreftEventDTO: SykmeldingBekreftEventDTO, sykmeldingId: String) {
        // tilgangskontroll!
        sykmeldingStatusKafkaProducer.send(sykmeldingBekreftEventDTO.toSykmeldingStatusKafkaEvent(sykmeldingId))
    }
}
