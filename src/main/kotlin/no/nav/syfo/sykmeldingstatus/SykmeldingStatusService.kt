package no.nav.syfo.sykmeldingstatus

import no.nav.syfo.sykmeldingstatus.api.SykmeldingBekreftEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingSendEventDTO
import no.nav.syfo.sykmeldingstatus.api.SykmeldingStatusEventDTO
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.toSykmeldingStatusKafkaEventDTO

class SykmeldingStatusService(private val sykmeldingStatusKafkaProducer: SykmeldingStatusKafkaProducer) {

    fun registrerStatus(sykmeldingStatusEventDTO: SykmeldingStatusEventDTO, sykmeldingId: String, source: String) {
        // tilgangskontroll!
        sykmeldingStatusKafkaProducer.send(sykmeldingStatusEventDTO.toSykmeldingStatusKafkaEventDTO(sykmeldingId), source)
    }

    fun registrerSendt(sykmeldingSendEventDTO: SykmeldingSendEventDTO, sykmeldingId: String, source: String) {
        // tilgangskontroll!
        sykmeldingStatusKafkaProducer.send(sykmeldingSendEventDTO.toSykmeldingStatusKafkaEventDTO(sykmeldingId), source)
    }

    fun registrerBekreftet(sykmeldingBekreftEventDTO: SykmeldingBekreftEventDTO, sykmeldingId: String, source: String) {
        // tilgangskontroll!
        sykmeldingStatusKafkaProducer.send(sykmeldingBekreftEventDTO.toSykmeldingStatusKafkaEventDTO(sykmeldingId), source)
    }
}
