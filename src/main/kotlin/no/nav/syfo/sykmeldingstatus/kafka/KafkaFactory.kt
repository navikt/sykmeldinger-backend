package no.nav.syfo.sykmeldingstatus.kafka

import no.nav.syfo.Environment
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.sykmeldingstatus.kafka.producer.SykmeldingStatusKafkaProducer
import no.nav.syfo.sykmeldingstatus.kafka.util.JacksonKafkaSerializer
import org.apache.kafka.clients.producer.KafkaProducer

class KafkaFactory private constructor() {
    companion object {
        fun getSykmeldingStatusKafkaProducer(environment: Environment): SykmeldingStatusKafkaProducer {
            val kafkaStatusProducerConfig = KafkaUtils.getAivenKafkaConfig().toProducerConfig(
                "${environment.applicationName}-producer", JacksonKafkaSerializer::class
            )
            val kafkaProducer = KafkaProducer<String, SykmeldingStatusKafkaMessageDTO>(kafkaStatusProducerConfig)
            return SykmeldingStatusKafkaProducer(kafkaProducer, environment.sykmeldingStatusTopic)
        }
    }
}
