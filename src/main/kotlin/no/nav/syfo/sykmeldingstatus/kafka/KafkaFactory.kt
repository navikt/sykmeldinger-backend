package no.nav.syfo.sykmeldingstatus.kafka

import no.nav.syfo.Environment
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.util.JacksonKafkaSerializer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import java.util.Properties

class KafkaFactory private constructor() {
    companion object {
        fun getSykmeldingStatusKafkaProducer(kafkaBaseConfig: Properties, environment: Environment): SykmeldingStatusKafkaProducer {
            val kafkaStatusProducerConfig = kafkaBaseConfig.toProducerConfig(
                "${environment.applicationName}-producer", JacksonKafkaSerializer::class
            )
            kafkaStatusProducerConfig[ProducerConfig.RETRIES_CONFIG] = 2
            val kafkaProducer = KafkaProducer<String, SykmeldingStatusKafkaMessageDTO>(kafkaStatusProducerConfig)
            return SykmeldingStatusKafkaProducer(kafkaProducer, environment.sykmeldingStatusTopic)
        }
    }
}
