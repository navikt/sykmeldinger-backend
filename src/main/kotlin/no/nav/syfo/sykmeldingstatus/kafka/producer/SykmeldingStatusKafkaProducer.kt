package no.nav.syfo.sykmeldingstatus.kafka.producer

import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.sykmeldingstatus.kafka.model.KafkaMetadataDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaEventDTO
import no.nav.syfo.sykmeldingstatus.kafka.model.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.utils.applog
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykmeldingStatusKafkaProducer(
    private val kafkaProducer: KafkaProducer<String, SykmeldingStatusKafkaMessageDTO>,
    private val topicName: String,
) {
    companion object {
        private val logger = applog()
        private const val SOURCE = "sykmeldinger-backend"
    }

    suspend fun send(sykmeldingStatusKafkaEventDTO: SykmeldingStatusKafkaEventDTO, fnr: String) {
        withContext(Dispatchers.IO) {
            logger.info(
                "Skriver statusendring {} for sykmelding med id {} til topic på aiven",
                sykmeldingStatusKafkaEventDTO.statusEvent,
                sykmeldingStatusKafkaEventDTO.sykmeldingId,
            )
            val metadataDTO =
                KafkaMetadataDTO(
                    sykmeldingId = sykmeldingStatusKafkaEventDTO.sykmeldingId,
                    timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                    fnr = fnr,
                    source = SOURCE,
                )
            val sykmeldingStatusKafkaMessageDTO =
                SykmeldingStatusKafkaMessageDTO(metadataDTO, sykmeldingStatusKafkaEventDTO)
            try {
                kafkaProducer
                    .send(
                        ProducerRecord(
                            topicName,
                            sykmeldingStatusKafkaMessageDTO.event.sykmeldingId,
                            sykmeldingStatusKafkaMessageDTO,
                        ),
                    )
                    .get()
            } catch (ex: Exception) {
                logger.error(
                    "Failed to send sykmeldingStatus to kafkatopic {}",
                    metadataDTO.sykmeldingId
                )
                throw ex
            }
        }
    }
}
