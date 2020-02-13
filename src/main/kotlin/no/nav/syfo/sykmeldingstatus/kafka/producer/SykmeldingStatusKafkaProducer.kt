package no.nav.syfo.sykmeldingstatus.kafka.producer

import java.time.ZonedDateTime
import no.nav.syfo.log
import no.nav.syfo.model.sykmeldingstatus.KafkaMetadataDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingStatusKafkaProducer(private val kafkaProducer: KafkaProducer<String, SykmeldingStatusKafkaMessageDTO>, private val topicName: String) {
    fun send(sykmeldingStatusKafkaEventDTO: SykmeldingStatusKafkaEventDTO, source: String) {
        log.info("Skriver statusendring for sykmelding med id {} til topic", sykmeldingStatusKafkaEventDTO.sykmeldingId)
        val metadataDTO = KafkaMetadataDTO(sykmeldingId = sykmeldingStatusKafkaEventDTO.sykmeldingId, timestamp = ZonedDateTime.now(), source = source)
        val sykmeldingStatusKafkaMessageDTO = SykmeldingStatusKafkaMessageDTO(metadataDTO, sykmeldingStatusKafkaEventDTO)
        kafkaProducer.send(ProducerRecord(topicName, sykmeldingStatusKafkaMessageDTO.event.sykmeldingId, sykmeldingStatusKafkaMessageDTO))
    }
}
